package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Module }
import info.vizierdb.commands.Commands
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class AppendModule(
  projectId: Identifier,
  branchId: Identifier,
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
{
  def handle: Response = 
  {
    val command = Commands.get(packageId, commandId)
    DB.autoCommit { implicit s => 
      val (branch, workflow): (Branch, Workflow) = 
        Branch.lookup(projectId, branchId)
              .getOrElse { 
                 return NoSuchEntityResponse()
              }
              .append(
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
        workflow.describe
      )
    }
  } 
}

object AppendModule
{
  implicit val format: Format[AppendModule] = Json.format
}