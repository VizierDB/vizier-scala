package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.MergeAttributesLensConfig
import org.mimirdb.lenses.Lenses

object MergeColumns
  extends LensCommand
{ 
  def lens = Lenses.picker
  def name: String = "Merge Columns"
  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = "schema", name = "Columns", components = Seq(
      ColIdParameter(id = "pickFrom", name = "Input")
    )),
    StringParameter(id = "pickAs", name = "Output")
  )
  def lensFormat(arguments: Arguments): String = 
    s"MERGE COLUMNS ${arguments.getList("schema").map { _.get[Int]("pickFrom").toString }.mkString(", ")} INTO ${arguments.get[String]("pickAs")}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    Json.toJson(
      MergeAttributesLensConfig(
        inputs = arguments.getList("schema")
                          .map { _.get[Int]("pickFrom") }
                          .map { schema(_).name },
        output = arguments.get[String]("pickAs"),
        dataType = None
      )
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
}