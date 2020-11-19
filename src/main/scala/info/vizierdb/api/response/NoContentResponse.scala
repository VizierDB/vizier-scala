package info.vizierdb.api.response

import org.mimirdb.api.Response
import javax.servlet.http.HttpServletResponse

case class NoContentResponse() extends Response
{
  def write(output: HttpServletResponse)
  {
    output.setStatus(HttpServletResponse.SC_NO_CONTENT)
  }
}
