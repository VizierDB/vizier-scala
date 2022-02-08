package info.vizierdb.vega

import play.api.libs.json._

class Chart(val mark: Mark)
{
  var root: TopLevelUnitSpec = TopLevelUnitSpec(
    `$schema` = Some(Vega.SCHEMA),
    description = Some("A simple bar chart with embedded data"),
    mark = MarkBar,
    // data = Some(InlineData(
    //   values = InlineDatasetAsArrayOfEmptyObject(
    //     Json.parse(
    //       s"""[{"a": "A", "b": 28}, {"a": "B", "b": 55}, {"a": "C", "b": 43},
    //           |{"a": "D", "b": 91}, {"a": "E", "b": 81}, {"a": "F", "b": 53},
    //           |{"a": "G", "b": 19}, {"a": "H", "b": 87}, {"a": "I", "b": 52}]
    //           |""".stripMargin
    //     ).as[Seq[JsObject]]
    //   )
    // )),
    encoding = Some(FacetedEncoding(
      x = Some(PositionFieldDef(
        field = Some(FieldAsString("a")), 
        `type` = Some(StandardTypeNominal),
        axis = Some(Axis(labelAngle = Some(AxisLabelAngleAsNumber(JsNumber(0)))))
      )),
      y = Some(PositionFieldDef(
        field = Some(FieldAsString("b")), 
        `type` = Some(StandardTypeQuantitative)
      )),
    ))
  )

  def description_=(x: String) = 
    { root.description = Some(x) }

  def data_=(x: Seq[JsObject]) = 
    { root.data = Some(InlineData(values = InlineDatasetAsArrayOfEmptyObject(x))) }

  def encoding: FacetedEncoding = 
    { if(root.encoding.isEmpty) { root.encoding = Some(FacetedEncoding())}; root.encoding.get }

  def export: JsValue = TopLevelSpecCodec.encode(root)

}