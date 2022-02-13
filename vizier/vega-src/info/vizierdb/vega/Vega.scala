package info.vizierdb.vega

import java.net.URL
import play.api.libs.json._

object Vega
{
  val SCHEMA = new URL("https://vega.github.io/schema/vega-lite/v5.2.0.json")

  /**
   * Create a bar chart with multiple adjacent bars with one record per row
   * @param   data   A sequence of maps with data.  Each map must contain a key for xaxis
   *                 and one for every element of yaxes
   * @param   xaxis  The attribute to use as an x axis
   * @param   yaxes  The attributes to use as individual bars.  
   * @param   ylabel The label for the y axis (default: concatenate all the elements of yaxes)
   * 
   * https://vega.github.io/vega-lite/docs/bar.html#grouped-bar-chart-multiple-measure-with-repeat
   */
  def multiBarChart(
    data: Seq[Map[String, JsValue]], 
    xaxis: String, 
    yaxes: Seq[String], 
    ylabel: String = null,
    title: String = null,
  ): Chart =
    new Chart(TopLevelRepeatSpecAsObject2(
      `$schema` = Some(Vega.SCHEMA),
      data = Some(InlineData(
                values = InlineDatasetAsArrayOfEmptyObject(data.map { JsObject(_) })
              )),
      repeat = LayerRepeatMapping( layer = yaxes ),
      spec = UnitSpec(
        mark = MarkBar,
        title = Option(title).map { TextAsString(_) },
        encoding = Some(Encoding(
          x = Some(PositionFieldDef(
            field = Some(FieldAsString(xaxis)), 
            `type` = Some(StandardTypeNominal)
          )),
          y = Some(PositionFieldDef(
            field = Some(RepeatRef(RepeatRefRepeatLayer)),
            `type` = Some(StandardTypeQuantitative),
            title = Option(ylabel).map { TextAsString(_) }
          )),
          color = Some(FieldOrDatumDefWithConditionDatumDefGradientStringNull(
            datum = Some(RepeatRef(RepeatRefRepeatLayer))
          )),
          xOffset = Some(ScaleDatumDef(
            datum = Some(RepeatRef(RepeatRefRepeatLayer))
          ))
        ))
      ),
    ))

  /**
   * Create a generic chart with multiple datasets aligned on x coordinates
   * @param   mark   The mark to use (e.g., MarkArea, MarkPoint)
   * @param   data   A sequence of maps with data.  Each map must contain a key for xaxis
   *                 and one for every element of yaxes
   * @param   xaxis  The attribute to use as an x axis
   * @param   yaxes  The attributes to use as individual bars.  
   * @param   ylabel The label for the y axis (default: concatenate all the elements of yaxes)
   * 
   * https://vega.github.io/vega-lite/docs/bar.html#grouped-bar-chart-multiple-measure-with-repeat
   */
  def multiChart(
    mark:  AnyMark,
    data: Seq[Map[String, JsValue]], 
    xaxis: String, 
    yaxes: Seq[String], 
    ylabel: String = null,
    title: String = null,
  ): Chart =
    new Chart(TopLevelRepeatSpecAsObject2(
      `$schema` = Some(Vega.SCHEMA),
      data = Some(InlineData(
                values = InlineDatasetAsArrayOfEmptyObject(data.map { JsObject(_) })
              )),
      repeat = LayerRepeatMapping( layer = yaxes ),
      spec = UnitSpec(
        mark = mark,
        title = Option(title).map { TextAsString(_) },
        encoding = Some(Encoding(
          x = Some(PositionFieldDef(
            field = Some(FieldAsString(xaxis)), 
            `type` = Some(StandardTypeNominal)
          )),
          y = Some(PositionFieldDef(
            field = Some(RepeatRef(RepeatRefRepeatLayer)),
            `type` = Some(StandardTypeQuantitative),
            title = Option(ylabel).map { TextAsString(_) }
          )),
          color = Some(FieldOrDatumDefWithConditionDatumDefGradientStringNull(
            datum = Some(RepeatRef(RepeatRefRepeatLayer))
          )),
          shape = 
            if(mark == MarkPoint){
              Some(FieldOrDatumDefWithConditionDatumDefStringNull(
                datum = Some(RepeatRef(RepeatRefRepeatLayer))
              ))
            } else { None },
        ))
      ),
    ))
}