package info.vizierdb.api.response

import play.api.libs.json._
import javax.servlet.http.HttpServletResponse
import org.mimirdb.api.BytesResponse

case class NoSuchEntityResponse () extends BytesResponse
{

  def getBytes = Json.obj("message" -> "Not Found").toString.getBytes
  
  override def write(output: HttpServletResponse)
  { 
    output.setStatus(HttpServletResponse.SC_NOT_FOUND)
    super.write(output)
  }
}