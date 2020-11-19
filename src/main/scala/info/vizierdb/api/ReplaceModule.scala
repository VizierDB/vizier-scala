package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Module }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class ReplaceModule(
  projectId: Identifier,
  branchId: Identifier,
  moduleId: Identifier,
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
{
  def handle: Response = 
  {
    DB.autoCommit { implicit s => 
      val branch: Branch = 
        Branch.lookup(projectId, branchId)
               .getOrElse { 
                 return NoSuchEntityResponse()
               }
      val cell =
        branch.head
              .cellByModuleId(moduleId)
              .getOrElse { 
                 return NoSuchEntityResponse()
               }

      val (_, workflow) = branch.update(cell.position, 
        Module.make(
          packageId = packageId,
          commandId = commandId,
          arguments = JsObject(
            arguments.as[Seq[Map[String, JsValue]]]
                     .map { arg =>
                       arg("id").as[String] -> 
                        arg("value")
                     }
                     .toMap
          ),
          revisionOfId = None
        )
      )

      RawJsonResponse(
        branch.summarize
      )
    }
  } 
}

object ReplaceModule
{
  implicit val format: Format[ReplaceModule] = Json.format
}