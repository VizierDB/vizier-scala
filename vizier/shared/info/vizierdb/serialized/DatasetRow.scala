package info.vizierdb.serialized

import info.vizierdb.types.RowIdentifier
import info.vizierdb.nativeTypes.JsValue

case class DatasetRow(
  id: RowIdentifier,
  values: Seq[JsValue],
  rowAnnotationFlags: Option[Seq[Boolean]],
  rowIsAnnotated: Option[Boolean]
)
