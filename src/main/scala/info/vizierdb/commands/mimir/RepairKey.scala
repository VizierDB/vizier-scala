package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.Lenses

object RepairKey 
  extends LensCommand
{ 
  def lens = Lenses.repairKey
  def name: String = "Fix Key Column"
  def lensParameters: Seq[Parameter] = Seq(
    TemplateParameters.COLUMN,
  )
  def lensFormat(arguments: Arguments): String = 
    s"REPAIR KEY COLUMN ${arguments.get[Int]("col")}"
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    Json.obj(
      "key" -> schema(arguments.get[Int]("col")).name
    )
  }
}