package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField

object RepairKey 
  extends LensCommand
{ 
  def lens = "repair_key"
  def name: String = "Fix Key Column"
  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "col", name = "Column")
  )
  def lensFormat(arguments: Arguments): String = 
    s"REPAIR KEY COLUMN ${arguments.get[Int]("col")}"
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    val schema = context.datasetSchema(dataset).get
    Json.obj(
      "key" -> schema(arguments.get[Int]("col")).name
    )
  }
}