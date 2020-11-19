package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Branch, Workflow }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class SourceBranch(
  branchId: Identifier,
  workflowId: Option[Identifier],
  moduleId: Option[Identifier]
)

object SourceBranch
{
  implicit val format: Format[SourceBranch] = Json.format
}

case class CreateBranch(
  projectId: Identifier,
  source: Option[SourceBranch],
  properties: JsObject
)
  extends Request
{
  def handle: Response = 
  {
    val branchName =
      properties.value
                .get("name")
                .map { _.as[String] }
                .getOrElse { "Untitled Branch" }
    DB.autoCommit { implicit s => 
      val (project, branch, workflow): (Project, Branch, Workflow) = 
        Project.lookup(projectId)
               .getOrElse { 
                 return NoSuchEntityResponse()
               }
               .createBranch(
                 name = branchName,
                 properties = properties,
                 fromBranch = source.map { _.branchId },
                 fromWorkflow = source.flatMap { _.workflowId }
               )
      RawJsonResponse(
        branch.summarize,
        status = Some(HttpServletResponse.SC_CREATED)
      )
    }
  } 
}

object CreateBranch
{
  implicit val format: Format[CreateBranch] = Json.format
}