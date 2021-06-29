package info.vizierdb.api.websocket

import play.api.libs.json.JsValue
import info.vizierdb.api.handler.ClientConnection
import java.io.{ InputStream, StringBufferInputStream }
import play.api.libs.json.JsObject

class WebsocketRequestConnection(request: JsObject)
  extends ClientConnection
{
  lazy val getInputStream: InputStream = 
    new StringBufferInputStream(request.toString())

  override def getJson: JsValue = request
  def getParameter(name: String): String = 
    (request \ name).asOpt[String].getOrElse { null }
  def getPart(name: String) =
    throw new UnsupportedOperationException(
      "Websocket requests don't support file transfer"
    )
}