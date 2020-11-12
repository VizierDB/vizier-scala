package info.vizierdb.api

import java.time.format.DateTimeFormatter
import play.api.libs.json._
import org.mimirdb.api.{ Request, JsonResponse }
import info.vizierdb.VizierAPI
import info.vizierdb.util.HATEOAS
import info.vizierdb.commands.Commands

case class ServiceDescriptorRequest()
  extends Request
{
  def handle = 
  {
    RawJsonResponse(
      Json.obj(
        "name" -> JsString(VizierAPI.NAME),
        "startedAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(VizierAPI.started),
        "defaults" -> Json.obj(
          "maxFileSize" -> VizierAPI.MAX_UPLOAD_SIZE,
          "maxDownloadRowLimit" -> VizierAPI.MAX_DOWNLOAD_ROW_LIMIT
        ),
        "environment" -> Json.obj(
          "name" -> VizierAPI.SERVICE_NAME,
          "version" -> VizierAPI.VERSION,
          "backend" -> VizierAPI.BACKEND,
          "packages" -> Commands.toJson
        ),
        HATEOAS.LINKS -> HATEOAS(
          HATEOAS.SELF           -> VizierAPI.urls.serviceDescriptor,
          HATEOAS.API_DOC        -> VizierAPI.urls.apiDoc,
          HATEOAS.PROJECT_CREATE -> VizierAPI.urls.createProject,
          HATEOAS.PROJECT_LIST   -> VizierAPI.urls.listProjects,
          HATEOAS.PROJECT_IMPORT -> VizierAPI.urls.importProject,
        )
      )
    )
  }
}
