package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions.Expression
import com.fasterxml.jackson.module.scala.deser.overrides
import play.api.libs.json._
import org.apache.spark.sql.catalyst.expressions.{
  Literal,
  Cast
}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import org.apache.spark.sql.catalyst.analysis.UnresolvedFunction
import org.apache.spark.sql.functions
import info.vizierdb.spark.SparkExpression.expressionFormat
case class UpdateRule(
  expression: Expression,
  frame: ReferenceFrame,
  id: Long
)
{
  /**
   * Returns true if this cell's expression involves exclusively references
   * to the local row
   */
  def isLocal = 
    rvalues.forall { 
      case _:SingleCell => true
      case OffsetCell(_, 0) => true
      case OffsetCell(_, _) => false
    }

  def rvalues: Seq[RValue] =
    expression.collect {
      case RValueExpression(rvalue) => rvalue
    }

  /**
   * Compute the set of source ranges that would affect an update
   * to the provided target expression when computing its value for
   * rows in the provided range.
   * @param    from         The first row that this update is used to compute
   * @param    to           The last row that this update is used to compute
   * @param    targetFrame  The [[ReferenceFrame]] in which from/to are specified
   * @return                A list of [[ColumnRef]], [[RangeSet]] tuples 
   *                        identifying the cells that the affected update cells 
   *                        depends on.
   */
  def triggeringRanges(from: Long, to: Long, targetFrame: ReferenceFrame = frame): Map[ColumnRef, RangeSet] =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseRange = offsetFrame.backward(RangeSet(from, to))
    rvalues.map { 
              case SingleCell(col, row) => col -> RangeSet(row, row)
              case OffsetCell(col, rowOffset) => col -> baseRange.offset(rowOffset)
            }
           .groupBy { _._1 }
           .mapValues { ranges =>
              offsetFrame.forward(
                ranges.map { _._2 }
                      .foldLeft(RangeSet()){ _ ++ _ }
              ) 
            }
  }

  /**
   * All cells that trigger re-execution of the specified cell with this rule
   * @param  target      The row on which this rule is evaluated
   * @param  targetFrame The [[ReferenceFrame]] in which target is specified
   * @return             A sequence of cells, relative to targetFrame, that can,
   *                     if modified, invalidate the cell this rule is used to 
   *                     compute.
   */
  def triggeringCells(target: Long, targetFrame: ReferenceFrame = frame): Seq[SingleCell] =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseTarget = offsetFrame.backward(RowByIndex(target))

    rvalues.flatMap { 
      case SingleCell(col, row) => 
        offsetFrame.forward(RowByIndex(row))
                   .collect { 
                      case RowByIndex(idx) => SingleCell(col, idx)
                    }
      case OffsetCell(col, 0) => 
        Some(SingleCell(col, target))
      case OffsetCell(col, rowOffset) => 
        assert(baseTarget.isInstanceOf[RowByIndex], "Ambiguous reference to offset row")
        offsetFrame.forward(RowByIndex(baseTarget.asInstanceOf[RowByIndex].idx+rowOffset))
                   .collect { 
                      case RowByIndex(idx) => SingleCell(col, idx)
                    }
    }
  }

  /**
   * Compute the ranges for which this update needs to be recomputed given
   * that one or more of its input rows were invalidated.
   * @param   from         The first row that was invalidated
   * @param   to           The last row that was invalidated
   * @param   column       The column in which rows were invalidated
   * @param   targetFrame  The reference frame of from and to
   * @return               The set of rows on which this update needs to be
   *                       re-evaluated.
   */
  def triggeredRanges(from: Long, to: Long, column: ColumnRef, targetFrame: ReferenceFrame = frame): RangeSet =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseRange = offsetFrame.backward(RangeSet(from, to))
    offsetFrame.forward(
      rvalues.filter { _.column == column }
             .map { 
                case SingleCell(_, row) => baseRange
                case OffsetCell(_, rowOffset) => baseRange.offset(-rowOffset)
             }
             .foldLeft(RangeSet()) { _ ++ _ }
    )
  }

  override def toString = s"{${expression.toString}}[$id]"
}
object UpdateRule {
  
  implicit val updateRuleWrites = Json.writes[UpdateRule]

  implicit val updateRuleReads = new Reads[UpdateRule] {
    def reads(j: JsValue): JsResult[UpdateRule] = {
      val id = (j \ "id").as[Long]
      val frame = (j \ "frame").as[ReferenceFrame]
      val expression = expr((j \ "expression").as[String]).expr.transform{
        case UnresolvedFunction(Seq("rvalueexpression"), Seq(expressionDetails), _, _, _) =>
          {
          //println("rvalueexpression")
          expressionDetails(0) match {
            case UnresolvedFunction(Seq("OffsetCell"), offsetDetails, _, _, _) =>
              {
                //println("offset cell")
                val refDetails = offsetDetails(0).toString().replaceAll("'", "").split(", ")
                val columnLabel = refDetails(0)
                val columnId = refDetails(1).toInt
                val columnRef = ColumnRef(columnId)
                columnRef.label = columnLabel
                val offset = offsetDetails(1).asInstanceOf[Literal].value.asInstanceOf[Int]
                val rvalue: RValue = OffsetCell(columnRef, offset)
                RValueExpression(rvalue)
              }
            case UnresolvedFunction(Seq("SingleCell"), rowDetails, _, _, _) =>
              {
                //println("single cell")
                println(rowDetails(0).toString)
                val refDetails = rowDetails(0).toString().replaceAll("'", "").split(", ")
                val columnLabel = refDetails(0)
                val columnId = refDetails(1).toInt
                val columnRef = ColumnRef(columnId)
                columnRef.label = columnLabel
                val row = rowDetails(1).asInstanceOf[Literal].value.asInstanceOf[Int]
                val rvalue: RValue = SingleCell(columnRef, row)
                RValueExpression(rvalue)
              }
            case _ => 
              {
                println("Couldn't identify RValue expression")
                null
              }

          } 
        }  
         case UnresolvedFunction(Seq("rvalueexpression"), _, _, _, _) =>   
          println("some undefined rvalueexpression")
          null
        }
        JsSuccess(UpdateRule(expression, frame, id))
      }
    }
  
  //implicit val updateRuleFormat: Format[UpdateRule] = Json.format
  
 }
