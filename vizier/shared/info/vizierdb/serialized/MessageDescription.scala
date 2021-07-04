package info.vizierdb.serialized

import info.vizierdb.types.MessageType
import info.vizierdb.nativeTypes.JsValue

case class MessageDescription(
  `type`: MessageType.T,
  value: JsValue
)
