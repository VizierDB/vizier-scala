package info.vizierdb.api.response

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.Response

case class CORSPreflightResponse(methodStrings: String*)
  extends Response
{
  val methods = methodStrings.mkString(",")

  def write(output: HttpServletResponse) =
  {
    output.setHeader("Access-Control-Allow-Headers", Seq(
      "content-type"
    ).mkString(", "))
    output.setHeader("Allow", methods)
    output.setHeader("Access-Control-Allow-Methods", methods)
  }
}