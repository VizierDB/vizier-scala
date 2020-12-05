package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._

object RepairKey 
  extends LensCommand
{ 
  def lens = "repair_key"
  def name: String = "Fix Key Column"
  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "col", name = "Column")
  )
  def lensFormat(arguments: Arguments): String = 
    s"REPAIR KEY ${arguments.get[Int]("col")}"
  def formatUpdatedArguments(lensArgs: JsValue): Map[String,JsValue] = Map.empty
  def lensArguments(arguments: Arguments, dataset: String, context: ExecutionContext): JsValue =
  {
    val schema = context.datasetSchema(dataset).get
    Json.obj(
      "key" -> schema(arguments.get[Int]("col")).name
    )
  }
}