package info.vizierdb.artifacts

import play.api.libs.json._
import info.vizierdb.types.Identifier
import org.apache.spark.sql.types.StructField
import org.mimirdb.spark.Schema

case class DatasetColumn(
  id: Identifier,
  name: String,
  `type`: String
)
{
  def toSpark: StructField =
    StructField(name, Schema.decodeType(`type`))
}
object DatasetColumn
{
  implicit val format: Format[DatasetColumn] = Json.format
}

case class DatasetRow(
  id: Identifier,
  values: Seq[JsValue]
)
object DatasetRow
{
  implicit val format: Format[DatasetRow] = Json.format
}

case class DatasetAnnotation(
  columnId: Identifier,
  rowId: Identifier,
  key: String,
  value: String
)
object DatasetAnnotation
{
  implicit val format: Format[DatasetAnnotation] = Json.format
}

case class Dataset(
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  annotations: Option[Seq[DatasetAnnotation]]
)
object Dataset
{
  implicit val format: Format[Dataset] = Json.format
}