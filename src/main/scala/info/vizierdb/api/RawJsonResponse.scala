package info.vizierdb.api

import play.api.libs.json._
import org.mimirdb.api.JsonResponse

case class RawJsonResponse(data: JsValue) extends JsonResponse[RawJsonResponse]

object RawJsonResponse
{
  implicit val format:Format[RawJsonResponse] = Format[RawJsonResponse](
    new Reads[RawJsonResponse] { def reads(j: JsValue) = JsSuccess(RawJsonResponse(j)) },
    new Writes[RawJsonResponse] { def writes(v: RawJsonResponse) = v.data }
  )
}
