package info.vizierdb.api.handler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import java.io.InputStream

abstract class ClientConnection {
  def getPathInfo: String
  def getInputStream: InputStream
  def getPartInputStream(part: String): InputStream
  def getParameter(name: String): String
}

class JettyClientConnection(request: HttpServletRequest, response: HttpServletResponse)
  extends ClientConnection
{
  def getPathInfo: String = request.getPathInfo
  def getInputStream: InputStream = request.getInputStream()
  def getPartInputStream(part: String): InputStream = 
  {
    Option(request.getPart(part))
      .getOrElse {
        throw new IllegalArgumentException(s"Parameter '$part' not provided")
      }
      .getInputStream()
  }
  def getParameter(name: String): String = 
    request.getParameter(name)
}