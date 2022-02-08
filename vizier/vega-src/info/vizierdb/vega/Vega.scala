package info.vizierdb.vega

import java.net.URL

object Vega
{
  val SCHEMA = new URL("https://vega.github.io/schema/vega-lite/v5.2.0.json")
  def barChart(
    data: Iterable[(String, Double)]
  ): Chart = 
  {
    val chart = new Chart(MarkBar)
    chart.data = data.map { case (g, v) => Json.obj("g" -> g, "v" -> v) }.toSeq
    chart.

  // def barChart: 
}