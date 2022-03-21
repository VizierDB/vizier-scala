package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions.SortOrder
import info.vizierdb.types._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ReferenceFrame(transformations: Seq[RowTransformation] = Seq.empty)
{
  
  def needsTranform = !transformations.isEmpty

  def forward(sourceRow: RowReference): Option[RowReference] = 
    transformations.foldLeft(Some(sourceRow):Option[RowReference]) { 
      case (Some(row), xform) => xform.forward(row) 
      case (None, _) => None
    }

  def forward(sourceRows: RangeSet): RangeSet = 
    transformations.foldLeft(sourceRows) { 
      (rows, xform) => xform.forward(rows)
    }

  def forward(row: Long): Option[Long] =
    transformations.foldLeft(Some(row):Option[Long]) {
      case (Some(row), xform) => xform.forward(row)
      case (None, _) => None
    }

  def backward(sourceRow: RowReference): RowReference = 
    transformations.foldRight(sourceRow) { _.backward(_) }

  def backward(sourceRows: RangeSet): RangeSet = 
    transformations.foldRight(sourceRows) { 
      (xform, rows) => xform.backward(rows)
    }

  def relativeTo(other: ReferenceFrame): ReferenceFrame = 
  {
    if(other.transformations.isEmpty){ return this }
    assert(
      transformations.take(other.transformations.size) == other.transformations,
      s"relativeTo on a divergent history:\nThis: ${transformations.map { "\n   "+_ }.mkString }\nOther: ${other.transformations.map { "\n   "+_ }.mkString }"
    )
    ReferenceFrame(transformations.drop(other.transformations.size))
  }

  def +(xform: RowTransformation): ReferenceFrame =
    ReferenceFrame(transformations :+ xform)
}
object ReferenceFrame
{
  implicit val rowTransfomationFormat: OFormat[RowTransformation] = Json.format[RowTransformation]
  
  implicit val rfSequenceWrites = new Writes[Seq[RowTransformation]] {
    def writes(s: Seq[RowTransformation]): JsValue =
      Json.toJson(s)
  }
  
  implicit val rfSequenceReads = new Reads[Seq[RowTransformation]] {
    def reads(j: JsValue): JsResult[Seq[RowTransformation]] =
      JsSuccess(j.as[Seq[RowTransformation]])
  }
  
  
    
  implicit val refrenceFrameFormat = Json.format[ReferenceFrame]
}