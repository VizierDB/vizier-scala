package info.vizierdb.api.handler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import java.io.InputStream

abstract class ClientConnection {
  def getInputStream: InputStream
  def getPart(part: String): (InputStream, String)
  def getParameter(name: String): String
}

class JettyClientConnection(request: HttpServletRequest, response: HttpServletResponse)
  extends ClientConnection
{
  def getInputStream: InputStream = request.getInputStream()
  def getPart(part: String): (InputStream, String) = 
  {
    val segment = request.getPart(part)
    if(segment == null){
      throw new IllegalArgumentException(s"Parameter '$part' not provided")
    }
    return (
      segment.getInputStream(),
      segment.getSubmittedFileName()
    )
  }
  def getParameter(name: String): String = 
    request.getParameter(name)
}