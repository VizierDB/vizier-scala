package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.api.request.Explain
import org.mimirdb.api.CaveatFormat._
import org.mimirdb.api.MimirAPI
import info.vizierdb.api.response._

case class GetArtifactRequest(
  projectId: Identifier, 
  artifactId: Identifier, 
  expectedType: Option[ArtifactType.T] = None, 
  offset: Option[Long] = None, 
  limit: Option[Int] = None, 
  forceProfiler: Boolean = false
)
  extends Request
{
  def getArtifact(expecting: Option[ArtifactType.T] = expectedType): Option[Artifact] = 
      DB.readOnly { implicit session => 
        Artifact.lookup(artifactId, Some(projectId))
      }.filter { artifact => 
        expecting.isEmpty || expecting.get.equals(artifact.t)
      }

  def handle: Response = 
  {
    getArtifact() match {
      case Some(artifact) => 
        return RawJsonResponse(
          artifact.describe(
            offset = offset, 
            limit = limit, 
            forceProfiler = forceProfiler
          )
        )
      case None => 
        return NoSuchEntityResponse() 
    }
  } 

  case class Annotations(columnId: Option[Int] = None, rowId: Option[String] = None) extends Request
  {
    def handle: Response =
    {
      getArtifact(Some(ArtifactType.DATASET)) match { 
        case Some(artifact) => 
          return RawJsonResponse(
            Json.toJson(
              Explain(
                s"SELECT * FROM ${artifact.nameInBackend}",
                rows = rowId.map { Seq(_) }.getOrElse { null },
                cols = columnId.map { col => 
                          Seq(
                            MimirAPI.catalog.get(artifact.nameInBackend)
                                    .schema(col)
                                    .name
                          )
                       }.getOrElse { null }
              )
            )
          )
      case None => 
        return NoSuchEntityResponse() 
      }
    }
  }

  object Summary extends Request
  {
    def handle: Response = 
    {
      getArtifact() match {
        case Some(artifact) => 
          return RawJsonResponse(
            artifact.summarize()
          )
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }

  object CSV extends Request
  {
    def handle: Response = 
    {
      getArtifact(Some(ArtifactType.DATASET)) match {
        case Some(artifact) => 
          ???
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }

  object File extends Request
  {
    def handle: Response =
    {
      getArtifact(Some(ArtifactType.FILE)) match {
        case Some(artifact) => 
          return FileResponse(
            file = artifact.file, 
            mimeType = artifact.mimeType, 
            name = artifact.jsonData
                           .as[Map[String, JsValue]]
                           .get("filename")
                           .map { _ .as[String] }
                           .getOrElse { s"unnamed_file_${artifact.id}" }
          )
        case None => 
          return NoSuchEntityResponse() 
      }
    }
  }
}