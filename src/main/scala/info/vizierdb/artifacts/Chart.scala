package info.vizierdb.artifacts

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, Row }
import org.mimirdb.api.request.ResultTooBig
import org.mimirdb.api.request.Query
import org.apache.spark.unsafe.types.UTF8String
import org.mimirdb.caveats.implicits._


case class ChartSeries(
  column: String, 
  label: Option[String], 
  constraint: Option[String]
){
  def schema: JsValue =
    Json.obj(
      "column" -> column,
      "label" -> JsString(label.getOrElse { column })
    )
}

case class Chart(
  dataset: String,
  name: String,
  chartType: String,
  grouped: Boolean,
  xaxis: Option[String],
  xaxisConstraint: Option[String],
  series: Seq[ChartSeries]
){
  def allColumns = 
    (xaxis.map { ChartSeries(_, None, xaxisConstraint) } ++: series)

  def schema: JsValue =
    Json.obj(
      "id"           -> JsNull,
      "dataset"      -> dataset,
      "name"         -> name,
      "data"         -> allColumns.map { _.schema },
      "chartType"    -> chartType,
      "groupedChart" -> grouped,
      "xAxis"        -> xaxis.map { _ => 0 }
    )

  def render(df: DataFrame): JsValue =
  {
    val query = 
      df.select(
        allColumns.map { case ChartSeries(col, _, constraint) =>
          // TODO: Don't ignore "constraint"
          df(col)
        }:_*
      )

    val result = 
      df.trackCaveats
        .stripCaveats
        .take(Query.RESULT_THRESHOLD+1)

    if(result.size >= Query.RESULT_THRESHOLD){
      throw new ResultTooBig()
    }


    def getColumn(column: String): Seq[JsValue] =
      result.map { 
        _.getAs[Any](column) match {
          case x:Integer => JsNumber(x.toLong)
          case x:Long => JsNumber(x)
          case x:Float => JsNumber(x)
          case x:Double => JsNumber(x)
          case x:String => JsString(x)
          case x:UTF8String => JsString(x.toString)
          case x => throw new IllegalArgumentException(s"Unsupported value ($x) in column $column")
        }
      }

    val seriesData = 
      series.map { case ChartSeries(column, label, _) =>
          Json.obj(
            "label" -> (label.map { _.trim } match {
                          case None => JsString(column)
                          case Some("") => JsString(column)
                          case Some(l) => JsString(l)
                        }),
            "data" -> getColumn(column),
            "caveats" -> result.map { _.isCaveatted(column) }
          )
        }

    return Json.obj(
      "data" -> schema,
      "result" -> JsObject(Map[String,JsValue](
        "chart" -> Json.obj(
          "type" -> chartType,
          "grouped" -> grouped
        ),
        "series" -> JsArray(seriesData)
      ) ++ xaxis.map { col => 
        "xAxis" -> Json.obj(
          "data" -> getColumn(col),
          "caveats" -> result.map { _.isCaveatted }
        )
      }.toMap)
    )
  }
}