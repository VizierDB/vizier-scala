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
import javax.servlet.http.HttpServletRequest
import info.vizierdb.filestore.Filestore
import java.io.FileOutputStream
import info.vizierdb.util.Streams
import org.eclipse.jetty.server.{ Request => JettyRequest }
import info.vizierdb.api.response._

case class CreateFile(
  projectId: Identifier,
  request: JettyRequest
)
  extends Request
{
  def handle: Response = 
  {
    val part = request.getPart("file")
    if(part == null){
      throw new IllegalArgumentException("No File Provided")
    }
    val content = part.getInputStream()

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
