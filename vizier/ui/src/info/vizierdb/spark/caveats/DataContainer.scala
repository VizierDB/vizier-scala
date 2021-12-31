package info.vizierdb.spark.caveats

import info.vizierdb.serialized.DatasetColumn
import info.vizierdb.nativeTypes.{ JsValue, Caveat }

/**
 * A js-native counterpart of the Mimir Data Container class.
 * 
 * This will be removed as soon as we merge Mimir into Vizier
 */
case class DataContainer (
  schema: Seq[DatasetColumn],
  data: Seq[Seq[JsValue]],
  prov: Seq[String],
  colTaint: Seq[Seq[Boolean]],
  rowTaint: Seq[Boolean],
  reasons: Seq[Seq[Caveat]],
  properties: Map[String,JsValue]
) 