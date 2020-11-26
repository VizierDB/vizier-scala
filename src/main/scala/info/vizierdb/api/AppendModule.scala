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
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.Scheduler

case class AppendModule(
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Option[Identifier],
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
    with LazyLogging
{
  def handle: Response = 
  {
    val command = Commands.get(packageId, commandId)

    val workflow: Workflow = 
      DB.autoCommit { implicit s => 
        logger.trace(s"Looking up branch $branchId")
        val branch:Branch =
          Branch.lookup(projectId, branchId)
                .getOrElse { 
                   return NoSuchEntityResponse()
                }

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }

        logger.trace(s"Creating Module: $packageId.$commandId($arguments)")

        val module = 
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

        logger.debug(s"Appending Module: $module")
        
        /* return */ branch.append(module)._2

      }

    logger.trace(s"Scheduling ${workflow.id}")
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)

    logger.trace("Building response")

    DB.readOnly { implicit s => 
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