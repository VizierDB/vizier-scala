package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.PivotLensConfig
import org.mimirdb.lenses.Lenses

object Pivot
  extends LensCommand
{ 
  def lens = Lenses.pivot
  def name: String = "Pivot Dataset"
  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "target", name = "Pivot Column"),
    ListParameter(id = "keys", name = "Group By", components = Seq(
      ColIdParameter(id = "column", name = "Column")
    )),
  )
  def lensFormat(arguments: Arguments): String = 
    s"PIVOT ON ${arguments.get[Int]("target")} GROUP BY ${arguments.getList("keys").map { _.get[Int]("column") }.mkString(", ")}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    val targetColumn = arguments.get[Int]("target")
    val keyColumns = arguments.getList("keys").map { _.get[Int]("column") }
    val valueColumns = ((0 until schema.size).toSet -- (targetColumn +: keyColumns)).toSeq

    Json.toJson(
      PivotLensConfig(
        target = schema(targetColumn).name,
        keys   = keyColumns.map { schema(_).name },
        values = valueColumns.map { schema(_).name },
        pivots = None
      )
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
}