package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.util.StupidReactJsonMap
import java.io.InputStream
import java.io.FileInputStream

case class ImportProject(
  projectExportPath: String
)
  extends Request
{
  def handle: RawJsonResponse = 
  {
    val project = info.vizierdb.export.ImportProject(new FileInputStream(projectExportPath))
     
    RawJsonResponse(
      project.summarize,
      status = Some(HttpServletResponse.SC_CREATED)
    )
  } 
}

object ImportProject
{
  implicit val format: Format[ImportProject] = Json.format
}