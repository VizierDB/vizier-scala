package info.vizierdb.export

import play.api.libs.json._

/**
 * An exported module in the classic Vizier notation (what we call a "cell" or "result")
 */
case class ExportedModule(
  id: String,
  state: Int,
  command: ExportedCommand,
  text: JsString,
  timestamps: ExportedTimestamps,
)

object ExportedModule
{
  implicit val format: Format[ExportedModule] = Json.format
}