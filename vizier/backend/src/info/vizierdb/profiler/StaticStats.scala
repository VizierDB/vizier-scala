package info.vizierdb.profiler

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField
import play.api.libs.json.{ Json, JsValue, JsNumber, JsString }
import DataProfiler.{ DatasetProperty, ColumnProperty, ColumnName } 

object StaticStats extends ProfilerModule
{
  val depends  = (Set(), Set())
  val provides = (Set(), Set("column"))

  def apply(df: DataFrame, inputs: (Map[DatasetProperty, JsValue], Map[ColumnName, Map[ColumnProperty, JsValue]])): 
    (Map[DatasetProperty, JsValue], Map[ColumnName, Map[ColumnProperty, JsValue]]) =
  {
    (
      Map(),
      df.schema
        .fields
        .zipWithIndex
        .map { case (StructField(name, t, _, _), idx) => 
          name -> 
            Map(
              "column" -> 
                Json.obj(
                  "id" -> JsNumber(idx),
                  "name" -> JsString(name),
                  "type" -> JsString(t.typeName)
                )
            )
        }.toMap
    )

  }
}