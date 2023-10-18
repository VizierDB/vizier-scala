package info.vizierdb.profiler

import org.apache.spark.sql.DataFrame
import play.api.libs.json.JsValue
import DataProfiler.{ DatasetProperty, ColumnProperty, ColumnName } 

trait ProfilerModule
{

  val depends:  (Set[DatasetProperty], Set[ColumnProperty])
  val provides: (Set[DatasetProperty], Set[ColumnProperty])

  def apply(df: DataFrame, inputs: (Map[DatasetProperty, JsValue], Map[ColumnName, Map[ColumnProperty, JsValue]])): 
    (Map[DatasetProperty, JsValue], Map[ColumnName, Map[ColumnProperty, JsValue]])
}


