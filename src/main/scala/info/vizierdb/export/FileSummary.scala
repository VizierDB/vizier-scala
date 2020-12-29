package info.vizierdb.export

import play.api.libs.json._

case class FileSummary(
  id: String,
  name: String,
  mimetype: Option[String]
) 

object FileSummary
{
  implicit val format: Format[FileSummary] = Json.format
}