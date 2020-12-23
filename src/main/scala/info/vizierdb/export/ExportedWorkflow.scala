package info.vizierdb.export

import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.types._

case class ExportedWorkflow(
  id: String,
  createdAt: ZonedDateTime,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  actionModule: Option[String],
  modules: Seq[String]
)
{
  val decodedAction = ActionType.decode(action)
}


object ExportedWorkflow
{
  implicit val format: Format[ExportedWorkflow] = Format(
    new Reads[ExportedWorkflow] {
      def reads(j: JsValue): JsResult[ExportedWorkflow] =
      {
        JsSuccess(
          ExportedWorkflow(
            id = (j \ "id").as[String],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            action = (j \ "action").as[String],
            packageId = (j \ "packageId").asOpt[String],
            commandId = (j \ "commandId").asOpt[String],
            actionModule = (j \ "actionModule").asOpt[String],
            modules = (j \ "modules").as[Seq[String]]
          )
        )
      }
    },
    Json.writes
  )
}