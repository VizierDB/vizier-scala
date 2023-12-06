package info.vizierdb.profiler

import org.apache.spark.sql.DataFrame
import play.api.libs.json._
import info.vizierdb.util.ExperimentalOptions
import play.api.libs.json.JsArray

object DataProfiler
{
  val IS_PROFILED = "is_profiled"

  type DatasetProperty = String
  type ColumnProperty = String
  type ColumnName = String

  val profilers = Seq[ProfilerModule](
    StaticStats,
    AggregateStats
  ) ++ (if(ExperimentalOptions.isEnabled("SKIP-HISTOGRAM")) { None }
        else { Some(HistogramStats) })

  def apply(df: DataFrame): Map[String, JsValue] = 
  {
    val (datasetProperties, columnProperties) = 
      profilers.foldLeft(
        (
          Map[DatasetProperty, JsValue](
            IS_PROFILED -> JsArray(Seq(JsString("mimir")))
          ), 
          df.columns.map { _ -> Map[ColumnProperty, JsValue]() }.toMap
        )
      ) { case (accum, profiler) => 
        val newFields = profiler(df, accum)
        (
          accum._1 ++ newFields._1,
          df.columns.map { col => 
            col -> (
              accum._2.getOrElse(col, { Map[ColumnProperty, JsValue]() })
                ++ newFields._2.getOrElse(col, { Map[ColumnProperty, JsValue]() })
            )
          }.toMap
        )
      }
    return datasetProperties + (
      "columns" -> 
        Json.toJson(
          df.columns
            .map { columnProperties.getOrElse(_, { Map[String, JsValue]()} ) }
            .toSeq
        )
    )
  }
}