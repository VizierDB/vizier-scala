package info.vizierdb.api

import java.io.File
import scalikejdbc.DB
import play.api.libs.json._
import java.io.InputStream
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Artifact }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.artifacts.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import org.mimirdb.api.request.LoadInlineRequest
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import info.vizierdb.filestore.Filestore
import java.io.FileOutputStream
import info.vizierdb.util.Streams
import org.eclipse.jetty.server.{ Request => JettyRequest }
import info.vizierdb.api.response._
import info.vizierdb.VizierServlet

case class CreateFile(
  projectId: Identifier,
  content: InputStream
)
  extends Request
{
  def handle: Response = 
  {
    DB.autoCommit { implicit s => 
      val project = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }

      val artifact = Artifact.make(
        project.id, 
        ArtifactType.FILE,
        "application/octet-stream",
        Array[Byte]()
      )

      val file: File = Filestore.get(project.id, artifact.id)

      Streams.cat(content, new FileOutputStream(file))

      RawJsonResponse(
        artifact.summarize()
      )
    }
  } 
}

object CreateFile
{
  def apply(projectId: Identifier, request: JettyRequest): CreateFile =
  {
    val part = request.getPart("file")
    if(part == null){
      throw new IllegalArgumentException("No File Provided")
    }
    CreateFile(projectId, part.getInputStream())
  }

  /**
   * Create a VizierServlet request handler for a CreateFile
   *
   * It's a little ugly, but we can't parse the request until we know which processor
   * is going to be used.  This abstraction-violating sacrifice exists mainly to keep
   * VizierAPI neat and tidy.
   */

  def handler(projectId: Identifier): ((HttpServletRequest, HttpServletResponse) => Unit) =
  {
    (request: HttpServletRequest, output: HttpServletResponse) => 
      VizierServlet.process(apply(projectId, request.asInstanceOf[JettyRequest]))(request, output)
  }

}
