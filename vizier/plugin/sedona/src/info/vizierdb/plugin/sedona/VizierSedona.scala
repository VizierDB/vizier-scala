package info.vizierdb.plugin.sedona

import org.apache.spark.sql.SparkSession

object VizierSedona {
  
  def init(spark: SparkSession): Unit = 
  {
    println("Sedona Initializing")
  }

  object Plugin extends info.vizierdb.Plugin(
    name = "Sedona",
    schema_version = 1,
    plugin_class = VizierSedona.getClass.getName(),
    description = "Vizier support for Apache Sedona",
    documentation = Some("https://sedona.apache.org/1.5.0/")
  )
}