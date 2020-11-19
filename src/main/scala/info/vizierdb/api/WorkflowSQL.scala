package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Artifact, ArtifactSummary }
import org.mimirdb.api.{Request, Response}
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.api.request.QueryMimirRequest
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response._

case class WorkflowSQLRequest(projectId: Identifier, branchId: Identifier, workflowId: Option[Identifier], query: String)
  extends Request
  with LazyLogging
{
  def handle: Response = 
  {
    DB.readOnly { implicit session => 
      val workflow: Workflow = 
        (workflowId match {
          case Some(workflowIdActual) => 
            Workflow.lookup(projectId, branchId, workflowIdActual)
          case None => 
            Branch.lookup(projectId, branchId).map { _.head }
        }).getOrElse {
          return NoSuchEntityResponse()
        }

      val artifacts: Seq[(String, ArtifactSummary)] = 
        workflow.outputArtifacts
                .map { a => a.userFacingName -> a.getSummary }
                // if summary returns None, this is a delete
                .flatMap { 
                  case (_, None) => None
                  case (name, Some(summary)) => Some(name -> summary) 
                }

      val datasets = 
        artifacts.filter { _._2.t.equals(ArtifactType.DATASET) }
                 .toMap
                 .mapValues { _.nameInBackend }

      val functions = 
        artifacts.filter { _._2.t.equals(ArtifactType.FUNCTION) } 
                 .toMap
                 .mapValues { _.nameInBackend }

      logger.trace(s"Query Tail: $query")
      return QueryMimirRequest(
        input = None,
        views = Some(datasets),
        query = query,
        includeUncertainty = Some(true),
        includeReasons = None
      ).handle
    }
  } 
}