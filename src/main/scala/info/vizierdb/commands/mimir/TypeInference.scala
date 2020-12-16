package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.MergeAttributesLensConfig
import org.mimirdb.lenses.Lenses
import org.mimirdb.spark.Schema
import org.mimirdb.spark.SparkPrimitive.dataTypeFormat

object TypeInference
  extends LensCommand
{ 
  def lens = Lenses.typeInference
  def name: String = "Assign Datatypes"
  def lensParameters: Seq[Parameter] = Seq(
    // TemplateParameters.SCHEMA
  )
  def lensFormat(arguments: Arguments): String = 
    "CAST TYPES"
    // s"CAST TYPES TO (${arguments.getList("schema").map { col => 
    //   s"${col.get[String]("schema_column")} ${col.get[String]("schema_datatype")}"
    // }.mkString(", ")})"


  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
    JsObject(Map[String,JsValue]())
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] =
    Map.empty
}