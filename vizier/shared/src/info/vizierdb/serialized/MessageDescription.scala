package info.vizierdb.serialized

import info.vizierdb.types.{ MessageType, StreamType }
import info.vizierdb.nativeTypes.JsValue

case class MessageDescription(
  `type`: MessageType.T,
  value: JsValue
)
{
  def t = `type`
  def withStream(stream: StreamType.T) =
    MessageDescriptionWithStream(
      `type` = `type`,
      value = value,
      stream = stream
    )
}

case class MessageDescriptionWithStream(
  `type`: MessageType.T,
  value: JsValue,
  stream: StreamType.T
)
{
  def t = `type`
}
