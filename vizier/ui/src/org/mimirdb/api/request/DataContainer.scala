package org.mimirdb.api.request

import info.vizierdb.serialized.DatasetColumn
import info.vizierdb.nativeTypes.{ JsValue, Caveat }

case class DataContainer (
  schema: Seq[DatasetColumn],
  data: Seq[Seq[Any]],
  prov: Seq[String],
  colTaint: Seq[Seq[Boolean]],
  rowTaint: Seq[Boolean],
  reasons: Seq[Seq[Caveat]],
  properties: Map[String,JsValue]
) 