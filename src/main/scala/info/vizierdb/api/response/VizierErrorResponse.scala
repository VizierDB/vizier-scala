package info.vizierdb.api.response

import play.api.libs.json._
import javax.servlet.http.HttpServletResponse
import org.mimirdb.api.JsonResponse

case class VizierErrorResponse (
            /* throwable class name */
                  category: String,
            /* throwable message */
                  message: String,
            /* error code */
                  status: Int = HttpServletResponse.SC_BAD_REQUEST
) extends JsonResponse[VizierErrorResponse]
{
  override def write(output: HttpServletResponse)
  { 
    output.setStatus(status)
    super.write(output)
  }
}

object VizierErrorResponse {
  implicit val format: Format[VizierErrorResponse] = Json.format

  def apply(error: Throwable, message: String): VizierErrorResponse = 
    apply(error, message, null)
  def apply(error: Throwable, message: String, className: String): VizierErrorResponse = 
    VizierErrorResponse(
      Option(className).getOrElse { error.getClass.getCanonicalName() } ,
      message
    )
}