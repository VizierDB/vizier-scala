package info.vizierdb.api

import javax.servlet.http.HttpServletResponse
import org.mimirdb.api.BytesResponse

case class NoSuchEntityResponse () extends BytesResponse
{

  def getBytes = "Not Found".getBytes()
  
  override def write(output: HttpServletResponse)
  { 
    output.setStatus(HttpServletResponse.SC_NOT_FOUND)
    super.write(output)
  }
}