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
import info.vizierdb.viztrails.Scheduler

case class InsertModule(
  projectId: Identifier,
  branchId: Identifier,
  modulePosition: Int,
  workflowId: Option[Identifier],
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
{
  def handle: Response = 
  {
    val command = Commands.get(packageId, commandId)

    val workflow: Workflow = 
      DB.autoCommit { implicit s => 
        val branch: (Branch) = 
          Branch.lookup(projectId, branchId)
                .getOrElse { 
                  return NoSuchEntityResponse()
                }
        val cell = 
          branch.head
                .cellByPosition(modulePosition)
                .getOrElse {
                  return NoSuchEntityResponse()
                }

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }
        
        val module = 
          Module.make(
            packageId = packageId,
            commandId = commandId,
            arguments = command.decodeReactArguments(arguments),
            revisionOfId = None
          )
        
        /* return */ branch.insert(cell.position, module)._2
      }

    Scheduler.schedule(workflow.id)

    DB.readOnly { implicit s => 
      RawJsonResponse(
        workflow.describe
      )
    }
  } 
}

object InsertModule
{
  implicit val format: Format[InsertModule] = Json.format
}