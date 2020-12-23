package info.vizierdb.export

import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.util.{ StupidReactJsonMap, StupidReactJsonField }

case class ExportedBranch(
  id: String,
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
  sourceBranch: Option[String],
  sourceWorkflow: Option[String],
  sourceModule: Option[String],
  isDefault: Boolean,
  properties: Seq[StupidReactJsonField],
  workflows: Seq[ExportedWorkflow]
)
{
  lazy val propertyMap = 
    StupidReactJsonMap.decode(properties)
  lazy val name = 
    propertyMap.get("name")
               .map { _.as[String] }
               .getOrElse("Untitled Branch")  
}


object ExportedBranch
{
  implicit val format: Format[ExportedBranch] = Format(
    new Reads[ExportedBranch] {
      def reads(j: JsValue): JsResult[ExportedBranch] =
      {
        JsSuccess(
          ExportedBranch(
            id = (j \ "id").as[String],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            lastModifiedAt = ExportedTimestamps.parseDate( (j \ "lastModifiedAt").as[String]),
            sourceBranch = (j \ "sourceBranch").asOpt[String],
            sourceWorkflow = (j \ "sourceWorkflow").asOpt[String],
            sourceModule = (j \ "sourceModule").asOpt[String],
            isDefault = (j \ "isDefault").as[Boolean],
            properties = (j \ "properties").as[Seq[StupidReactJsonField]],
            workflows = (j \ "workflows").as[Seq[ExportedWorkflow]]
          )
        )
      }
    },
    Json.writes
  )
}