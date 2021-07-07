package info.vizierdb.api.websocket

import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.JsValue
import info.vizierdb.delta.WorkflowDelta

case class WebsocketRequest(
  id: Identifier,
  path: Seq[String],
  args: Map[String, JsValue]
)

sealed trait WebsocketResponse
{
  def id: Identifier
}

case class NormalWebsocketResponse(
  id: Identifier,
  response: JsValue
) extends WebsocketResponse

case class ErrorWebsocketResponse(
  id: Identifier,
  message: String,
  detail: Option[String] = None
) extends WebsocketResponse

case class NotificationWebsocketMessage(
  delta: WorkflowDelta
) extends WebsocketResponse { def id = 0 }