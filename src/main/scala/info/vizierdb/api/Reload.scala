package info.vizierdb.api

import play.api.libs.json._
import org.mimirdb.api.{ Request, JsonResponse }
import info.vizierdb.VizierAPI
import info.vizierdb.util.HATEOAS
import info.vizierdb.commands.Commands

object ReloadRequest
  extends Request
{
  // Archaic... we don't need this anymore, but produce 
  // *some* response in case the UI needs it
  def handle = 
  {
    RawJsonResponse(
      Json.obj(
        "success" -> true
      )
    )
  }
}
