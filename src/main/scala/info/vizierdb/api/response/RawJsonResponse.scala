package info.vizierdb.api.response

import play.api.libs.json._
import org.mimirdb.api.JsonResponse
import javax.servlet.http.HttpServletResponse

case class RawJsonResponse(
  data: JsValue, 
  status: Option[Int] = None
) extends JsonResponse[RawJsonResponse]
{
  override def write(output: HttpServletResponse)
  {
    status.foreach { output.setStatus(_) }
    super.write(output)
  }
}

object RawJsonResponse
{
  implicit val format:Format[RawJsonResponse] = Format[RawJsonResponse](
    new Reads[RawJsonResponse] { def reads(j: JsValue) = JsSuccess(RawJsonResponse(j)) },
    new Writes[RawJsonResponse] { def writes(v: RawJsonResponse) = v.data }
  )
}
