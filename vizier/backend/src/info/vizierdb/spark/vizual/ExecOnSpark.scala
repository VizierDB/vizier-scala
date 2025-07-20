/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.spark.vizual

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, Column }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ StructField, StringType, DataType }
import org.apache.spark.sql.catalyst.expressions.{ Expression, Cast, Literal }
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import info.vizierdb.spark.SparkPrimitive
import org.mimirdb.caveats.implicits._
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT

object ExecOnSpark
  extends LazyLogging
{
  def apply(input: DataFrame, script: Seq[VizualCommand]): DataFrame =
    script.foldLeft(input) { apply(_, _) }

  def apply(input: DataFrame, command: VizualCommand): DataFrame =
  {
    logger.debug(s"Applying $command")
    logger.trace(s"   ... on: $input") 
    command match { 
      case DeleteColumn(position) => 
        {
          val (pre, post) = input.columns.splitAt(position)
          input.select((pre.toSeq ++ post.tail).map { input(_) }:_*)
        }
      case DeleteRow(rowid) => 
        {
          AnnotateWithRowIds.withRowId(input) { df => 
            df.filter(col(AnnotateWithRowIds.ATTRIBUTE) =!= lit(rowid))
          }
        }
      case InsertColumn(position, column, dataType) => 
        {
          val columns = 
            input.columns
                 .map { input(_) }
                 .toSeq
          val (pre, post):(Seq[Column], Seq[Column]) = 
            position.map { columns.splitAt(_) }
                    .getOrElse { (columns, Seq()) }
          input.select( ((pre :+ lit(null).cast(dataType.getOrElse { StringType }).as(column)) ++ post):_* )
        }
      case InsertRow(positionMaybe, values) =>  
        {
          val position = positionMaybe.getOrElse { -1l }
          val newRowData = 
            input.schema
                 .zip(values.getOrElse { input.columns.toSeq.map { _ => JsNull } })
                 .map { case (field, value) => 
                   new Column(Literal(
                     userFacingToInternalType(SparkPrimitive.decode(value, field.dataType), field.dataType),
                     field.dataType
                   )).as(field.name)
                 }
          if(position < 0){
            input.union(
              input.sqlContext
                   .range(1)
                   .select(newRowData:_*)
            )
          } else {
            AnnotateWithSequenceNumber.withSequenceNumber(input){ df =>
              val seq = df(AnnotateWithSequenceNumber.ATTRIBUTE)
              val oldRowData =
                input.columns.map { df(_) } :+ 
                  when(seq >= position, seq + 1)
                    .otherwise(seq)
                    .as(AnnotateWithSequenceNumber.ATTRIBUTE)
              val newRowDataWithAttribute = 
                newRowData :+ lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE)
              val newRow = 
                input.sqlContext
                     .range(1)
                     .select(newRowDataWithAttribute:_*)

              df.select(oldRowData:_*)
                .union(newRow)
                .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
            }
          }
        }
      case MoveColumn(from, position) => 
        {
          val (preFrom, postFrom) = input.columns.splitAt(from)
          val otherColumns = preFrom ++ postFrom.tail
          val (preTo, postTo) = otherColumns.splitAt(position)
          val finalSchema = (preTo :+ postFrom.head) ++ postTo

          input.select( finalSchema.map { input(_) } :_* )
        }
      case MoveRow(row, position) => 
        {
          AnnotateWithRowIds.withRowId(input) { rowDF =>
            val targetDropped = 
              rowDF.filter( rowDF(AnnotateWithRowIds.ATTRIBUTE) =!= row )
            AnnotateWithSequenceNumber.withSequenceNumber(targetDropped){ df =>
              val seq = df(AnnotateWithSequenceNumber.ATTRIBUTE)
              val oldRowData =
                input.columns.map { df(_) } :+ 
                  when(seq >= position, seq + 1)
                    .otherwise(seq)
                    .as(AnnotateWithSequenceNumber.ATTRIBUTE) :+
                  df(AnnotateWithRowIds.ATTRIBUTE)
              val replacedRowData = 
                input.columns
                     .map { rowDF(_) } :+
                        lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE) :+
                  df(AnnotateWithRowIds.ATTRIBUTE)
              val replacedRow = 
                rowDF.filter( rowDF(AnnotateWithRowIds.ATTRIBUTE) === row )
                     .select(replacedRowData:_*)

              df.select(oldRowData:_*)
                .union(replacedRow)
                .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
            }
          }
        }
      case FilterColumns(columns) => 
        {
          val input_columns = input.columns
          input.select(
            columns.map { c => 
              input(input_columns(c.columns_column)).as(c.columns_name)
            }:_*
          )
        }
      case RenameColumn(position, name) => 
        {
          val newSchema: Array[Column] =
            input.columns
                 .zipWithIndex
                 .map { case (c, i) => if(i == position) { input(c).as(name) } 
                                       else { input(c) } }
          input.select(newSchema:_*)
        }
      case Sort(columns) => 
        {
          input.orderBy(
            columns.map { col =>
              val base = input(input.columns(col.column))
              if(col.order.toUpperCase().equals("ASC")) { 
                base.asc
              } else { 
                base.desc
              }
            }:_*
          )
        }
      case UpdateCell(column, rows, valueMaybe, comment) => 
        {
          val targetColumn: StructField = input.schema.fields(column)

          // Default to selecting all rows if no explicit row is given
          val selectedRows = rows.getOrElse { AllRows() }

          var base = col(targetColumn.name)
          
          // If the expression is prefixed with an '=', treat it as an interpreted expression
          // If it's empty, treat it as a null
          var update: Column = valueMaybe match {
            /////////////////////////////////////////////

            case None => {
              lit(null)
            }

            case Some(JsString("")) => {
              // We interpret blanks depending on the type of the column.  If the column is
              // string-typed, we treat it as a string.  If the column is not, we treat it as
              // a null.
              targetColumn.dataType match { case StringType => lit("") ; case _ => lit(null) }
            }

            /////////////////////////////////////////////

            case Some(JsString(value)) if value(0) == '=' => {
              // If the user gives us a formula, we have a bit more information about their intent.
              // If we can safely cast the expression type to the column type, we do that.
              // If not, maybe we can safely cast the column type to the expression type.  
              // Failing that, we're going to default to casting the column to the expression, but
              // we'll add a caveat to keep the user informed.
              val update = Resolve(expr(value.substring(1)), input)
              
              // Trivial case: the update and column types are the same!
              if(update.expr.dataType.equals(targetColumn.dataType)) {
                update // just return the update

              // If we're replacing all rows, the base type doesn't really matter.  Just use the
              // update as is and update the column type
              } else if(selectedRows.isAllRows) {
                update

              // If we can losslessly cast the expression result to the target column type, 
              // then do so and be happy.
              } else if(Cast.canUpCast(update.expr.dataType, targetColumn.dataType)) {

                // We can always safely up-cast to strings, so let's tack on a quick warning in
                // case we're about to do that.
                if(targetColumn.dataType.equals(StringType)){
                  update.cast(targetColumn.dataType)
                        .caveat(s"Automatically casting `${targetColumn.name} $value` from ${update.expr.dataType} to the native column type (${targetColumn.dataType}).  Add an explicit .cast() to silence this warning.")
                } else {
                  update.cast(targetColumn.dataType)
                }

              // We might also be able to do the reverse.  If we can upcast the column data
              // type to the expression type, do so.
              } else if(Cast.canUpCast(targetColumn.dataType, update.expr.dataType)) {
                base = base.cast(update.expr.dataType)
                update // just return the update as-is

              // If we've gotten to this point, we're going to lose *something*.  First, let's check
              // to see if the cast (in either direction) is is safe (even if lossless).  Let's 
              // also default to losing the minimum information possible (i.e., just the updated 
              // value).
              } else if(Cast.canCast(update.expr.dataType, targetColumn.dataType)) {
                update.cast(targetColumn.dataType)
                // Of course, we can still warn the user about what they've done.
                      .caveat(s"Updating `${targetColumn.name} $value` (${update.expr.dataType}) doesn't match the type of ${targetColumn.name} (${targetColumn.dataType}).  Add an explicit .cast() to fix this error.")
              
              // If this isn't a safe cast, let's try going the other way.
              } else if(Cast.canCast(targetColumn.dataType, update.expr.dataType)) {
                base = base.cast(update.expr.dataType)
                // Again, warn the user if we've broken things
                           .caveat(s"Update `${targetColumn.name} $value` forced me to change the type of ${targetColumn.name} from ${targetColumn.dataType} to ${update.expr.dataType}.  Add an explicit .cast() to fix this error.")
                update // and return the update

              // If all else fails, make everything a string.
              } else {
                val msg = s"Update `${targetColumn.name} $value` forced me to change the type of ${targetColumn.name} to string.  Add an explicit .cast() to fix this error."
                base = base.cast(StringType)
                           .caveat(msg)
                update.cast(StringType).caveat(msg)
              }

            }

            /////////////////////////////////////////////

            case Some(value) => {
              // If we're here, we've been given a literal to interpret.  This is a wee bit tricky
              // since we need to figure out how to cast it.  Start with the column's native data
              // type.
              // println(s"$value -> ${targetColumn.dataType}")
              val decoded = 
                SparkPrimitive.decode(
                  value, 
                  targetColumn.dataType, 
                  castStrings = true
                )
              // SparkPrimitive gives us user-facing spark objects.  Convert to
              // internal types if necessary
              val update = userFacingToInternalType(decoded, targetColumn.dataType)

              // If the updated value can't be interpreted in the column's native data type, 
              // make everything a string.  (eventually, maybe we try some inference to figure
              // out the type of the updated value, but this seems like a minimal-loss default for 
              // now)
              if(update == null) {
                base = base.cast(StringType)
                           .caveat(s"Couldn't interpret '$value' in ${targetColumn.name}'s native type (${targetColumn.dataType}), so I made the entire column a string.  Add an explicit .cast() to fix this error.")
                lit(SparkPrimitive.decode(value, StringType).toString) // and return the update string

              // If the updated value casts successfully, great
              } else {
                new Column(Literal(update, targetColumn.dataType))
              }
            }
          }

          logger.trace(s"   ... update before comment = $update")

          // Apply the comment if provided
          if(comment.isDefined){
            update = update.caveat(comment.get)
          }

          logger.trace(s"   ... update = $update")

          def rewriteTargetColumn(df: DataFrame, expr: Column) = 
          {
            logger.trace(s"   ... Rewriting ${targetColumn.name} <- $expr")
            val columns =
              df.schema
                .zipWithIndex
                .map { case (c, idx) => 
                   if(idx != column) { df(c.name) }
                   else { Resolve(expr.as(c.name), df) }
                }
            df.select(columns:_*)
          }

          selectedRows match {
            case _:AllRows | _:RowsByConstraint => {
              rewriteTargetColumn(input, selectedRows { update } { base })
            }

            case _:RowsById => {
              AnnotateWithRowIds.withRowId(input) { df => 
                rewriteTargetColumn(df, selectedRows { update } { base })
              }
            }
          }
        }
    }
  }

  /**
   * Spark uses different internal and user-facing formats.  Here, we want
   * to use user-facing formats, but when executing it's necessary to convert
   * literals to the internal format.
   */
  def userFacingToInternalType(value: Any, dataType: DataType): Any =
    dataType match {
      // change this to UserDefinedType once we upgrade to spark 3.2
      case GeometryUDT => GeometryUDT.serialize(value.asInstanceOf[org.locationtech.jts.geom.Geometry])

      // most native types can be converted by evaluating the literal expression
      case _ => lit(value).expr.eval()
    }

}