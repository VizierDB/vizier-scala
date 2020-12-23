package info.vizierdb.export

import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.util.{ StupidReactJsonMap, StupidReactJsonField }

case class ExportedProject(
  properties: Seq[StupidReactJsonField],
  defaultBranch: String,
  files: Seq[FileSummary],
  modules: Map[String, ExportedModule], 
  branches: Seq[ExportedBranch],
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
)
{
  lazy val propertyMap = 
    StupidReactJsonMap.decode(properties)
  lazy val name = 
    propertyMap.get("name")
               .map { _.as[String] }
               .getOrElse("Untitled Project")
  lazy val modulesByNewVizierId = 
    modules.values.map { module => 
      module.command.id.getOrElse { module.id } -> module
    }.toMap
}

object ExportedProject
{
  implicit val format: Format[ExportedProject] = Format(
    new Reads[ExportedProject] {
      def reads(j: JsValue): JsResult[ExportedProject] =
      {
        JsSuccess(
          ExportedProject(
            properties = (j \ "properties").as[Seq[StupidReactJsonField]],
            defaultBranch = (j \ "defaultBranch").as[String],
            files = (j \ "files").as[Seq[FileSummary]],
            modules = (j \ "modules").as[Map[String, ExportedModule]],
            branches = (j \ "branches").as[Seq[ExportedBranch]],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            lastModifiedAt = ExportedTimestamps.parseDate( (j \ "lastModifiedAt").as[String])
          )
        )
      }
    },
    Json.writes
  )
}