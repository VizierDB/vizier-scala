package info.vizierdb.spreadsheet

import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.DefaultProvenance
import info.vizierdb.spark.DataFrameConstructor
import info.vizierdb.types
import org.apache.spark.sql.types.StructField
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import org.apache.spark.sql.DataFrame
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._
import scala.collection.mutable
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.Literal
import info.vizierdb.Vizier
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber

class SpreadsheetDatasetConstructor(
  source: Identifier,
  spreadsheet: EncodedSpreadsheet,
)
  extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{

    override def dependencies: Set[Identifier] = Set(source)

    override def schema: Seq[StructField] = 
      spreadsheet.schema.map { _.output }

    override def construct(context: Identifier => Artifact): DataFrame = 
    {
      val spreadsheet = this.spreadsheet.rebindVariables

      // June 6, 2023 by OK: The logic here (for now) is limited to supporting 
      // single-row expressions.  More interesting spreadsheets are targeted at
      // Vizier 2.1
      assert(spreadsheet.executor == "single_row")

      // RowIds 
      var df = 
        AnnotateWithSequenceNumber(context(source).dataframeFromContext(context))
      var rowid = df(AnnotateWithSequenceNumber.ATTRIBUTE)

      // df.show()


      logger.trace(s"Applying spreadsheet overlay to dataset (${df.count()} rows): ${df.schema.mkString(", ")}")

      // Translate RowIds in the *source* dataset to the rowids of the *target*
      // dataset based on the mappings in spreadsheet.rows
      // First step in this process: Generate a Case When expression that 
      // applies the appropriate mapping.
      logger.trace(s"Row mapping added: \n${spreadsheet.rows}")
      val rowIdMap = 
        spreadsheet.rows.data.iterator
          .map { case (low, high, position) => 
            (
              (rowid >= position) and (rowid <= (position + (high-low))), 
              (rowid - position + low)
            ) 
          }
          .foldLeft( null:Column ) { case (accum, (condition, map)) =>
            if(accum == null){ when(condition, map) }
            else { accum.when(condition, map) }
          }
          .otherwise(rowid)

      // Second step: actually apply the mapping.  While we're at it, transform
      // the schema into the target schema given in spreadsheet.schema
      df = df.select(
        (
          rowIdMap.as(AnnotateWithSequenceNumber.ATTRIBUTE) +:
          spreadsheet.schema.map { col =>
            col.source match {
              case SourceDataset(_, field) => df(field.name).as(col.output.name)
              case DefaultValue(default) => lit(default).as(col.output.name)
            }
          }
        ):_*
      )

      // df.show()

      // Records that don't have a rowid in the target dataset were deleted
      // filter those out now.
      // TODO: Figure out if the user has actually deleted a row, because 
      // if not, this query step is unnecessary.
      df = df.filter { not(isnull( df(AnnotateWithSequenceNumber.ATTRIBUTE) )) }

      // df.show()

      // Ranges of rowids that are below the row map's upper limit, but 
      // not explicitly defined in the row map were inserted.  Find these
      // ranges and insert them
      logger.trace(s"Row ranges added: ${spreadsheet.rows.invertedIterator.mkString(", ")}")
      df = spreadsheet.rows
            .invertedIterator
            .foldLeft(df) { case (df, (low, high)) =>
              df.unionAll(
                Vizier.sparkSession.range(low, high+1)
                      .select(
                        (
                          col("id").as(AnnotateWithSequenceNumber.ATTRIBUTE) +:
                          spreadsheet.schema.map { col =>
                            col.source match {
                              case SourceDataset(_, field) => lit(null).as(col.output.name)
                              case DefaultValue(default) => lit(default).as(col.output.name)
                            }
                          }
                        ):_*
                      )
              )
            }

      // df.show()

      rowid = df(AnnotateWithSequenceNumber.ATTRIBUTE)

      // Finally, apply the user-provided expressions.  

      // The main challenge here is that the expression for one column may 
      // depend on expressions for other columns.  In general, we want to
      // apply the rules in dependency order, but Spark's optimizer really 
      // doesn't like the large stacks of Project LogicalPlan nodes that
      // we'd get if we did that.  
      //
      // Instead, we're going to try to apply everything in a single go.
      // 
      // To make it feasible, we first subdivide the entire dataset into
      // ranges, with each range featuring a self-consistent set of 
      // UpdatePatterns.  (RangeMap.joinIterator does the heavy lifting)
      val updateMaps = spreadsheet.updateMaps

      logger.trace(s"Update Map: $updateMaps")

      val colNames = spreadsheet.schema
                        .map { col => col.ref -> col.output.name }.toMap

      val colDefaults = spreadsheet.schema
                        .map { col => col.ref -> updateMaps.get(col.ref).flatMap { _._2 } }.toMap

      // The following defines everything except the 'ELSE' clause of the
      // CASE WHEN condition
      val valueUpdateRules =
        RangeMap.joinIterator(
          spreadsheet.schema.map { col => 
            updateMaps.get(col.ref)
                      .map { _._1 }
                      .getOrElse { new RangeMap() } 
          }.toSeq:_*
        )
        .filter { _._3.exists { !_.isEmpty }}
        .toSeq
        .map { case (low, high, expressions) => 
          val exprMap: Map[ColumnRef, Option[UpdatePattern]] = 
            spreadsheet.schema.map { _.ref }.zip(expressions).toMap

          logger.trace(s"Generating segment [$low, $high] -> ${exprMap.mapValues { _.map { _.expression }}}")

          // We inline computations as needed, generating a single expression
          // for the target column.  The following (recursive) function
          // expands the expression into 
          def expandExpr(col: ColumnRef, visited: Set[ColumnRef]): Expression =
          {
            (exprMap.get(col) match {
              // if the column isn't defined at all, replace it with a null
              // Undefined refs can happen e.g., if the user deletes a referenced
              // column.  In this case 'null' roughly mimics the right behavior
              case None => Literal(null)
              // If the column exists, but doesn't have a pattern defined over
              // this segment, then fall through to the column default (if one 
              // exists) or the source dataset value for the column (if not)
              case Some(None) => 
                colDefaults(col) match {
                  case None => df(colNames(col)).expr
                  case Some(default) => default.expression
                }
              // And finally, if the column is defined. use it
              case Some(Some(pattern)) => pattern.expression
            }).transform {
              // Note: Any changes to this block MUST be reflected in the 
              // identical block below

              // Traverse the expression and recursively inline any 
              // references to columns
              case RValueExpression(OffsetCell(col, 0)) =>
                if(visited contains col){ 
                  // Cyclic references get turned into nulls
                  Literal(null)
                }
                else { 
                  expandExpr(col, visited + col)
                }
              case RValueExpression(ref) =>
                throw new UnsupportedOperationException(s"Unsupported rvalue: $ref")
            }
          }
          /* return */ (
            (rowid >= low) and (rowid <= high), 
            spreadsheet.schema.map { col => new Column(expandExpr(col.ref, Set(col.ref))) }
          ) 
        }
        .foldLeft(null:Seq[Column]) { case (accum, (condition, exprs)) => 
          if(accum == null){ 
            exprs.map { when(condition, _) }
          } else {
            accum.zip(exprs).map { case (a, e) => a.when(condition, e) }
          }
        }

      // This recursive function behaves exactly like expandExpr above, but
      // only operates over the colDefaults.  This is for every row that 
      // doesn't have *any* row-specific update patterns applied.
      def expandDefaults(col: ColumnRef, visited: Set[ColumnRef]): Expression =
      {
        colDefaults.get(col) match {
          case None => Literal(null)
          case Some(None) => df(colNames(col)).expr
          case Some(Some(pattern)) =>
            pattern.expression.transform {
              // Note: Any changes to this block MUST be reflected in the 
              // identical block above

              // Traverse the expression and recursively inline any 
              // references to columns
              case RValueExpression(OffsetCell(col, 0)) =>
                if(visited contains col){ 
                  // Cyclic references get turned into nulls
                  Literal(null)
                }
                else { 
                  expandDefaults(col, visited + col)
                }
              case RValueExpression(ref) =>
                throw new UnsupportedOperationException(s"Unsupported rvalue: $ref")
            }
        }
      }

      // Finally ensure the rows are in order and apply the updates
      df.orderBy(rowid)
        .select(
          valueUpdateRules
            .zip(spreadsheet.schema)
            .map { case (expr, col) =>
              expr.otherwise(new Column(expandDefaults(col.ref, Set(col.ref))))
                  .as(col.output.name)
            }:_*
        )

    }
}