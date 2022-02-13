package info.vizierdb.vega

import play.api.libs.json._

class Chart(var root: TopLevelSpec)
{
  root match {
    case t:TopLevelUnitSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelConcatSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelFacetSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelHConcatSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelVConcatSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelLayerSpec => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelRepeatSpecAsObject1 => t.`$schema` = Some(Vega.SCHEMA)
    case t:TopLevelRepeatSpecAsObject2 => t.`$schema` = Some(Vega.SCHEMA)
  }

  def export: JsValue = TopLevelSpecCodec.encode(root)
}