package info.vizierdb.api

import scalikejdbc.DB
import java.io.File
import info.vizierdb.types._
import info.vizierdb.export.{ ExportProject => DoExport }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.util.Streams
import info.vizierdb.catalog.Project
import java.io.FileOutputStream
import info.vizierdb.api.response.FileResponse

case class ExportProject(
  projectId: Identifier
)
  extends Request
{
  def handle: Response = 
  {
    val projectName = 
      DB.readOnly { implicit s => 
        Project.get(projectId)
               .name
      }

    val tempFile = File.createTempFile(s"project_$projectId", ".export")

    Streams.closeAfter(new FileOutputStream(tempFile)) { f => 
      DoExport(projectId = projectId, output = f)
    }

    FileResponse(
      file = tempFile, 
      name = projectName+".vizier", 
      mimeType = "application/octet-stream",
      afterCompletedTrigger = { () => tempFile.delete() }
    )
  }
}
