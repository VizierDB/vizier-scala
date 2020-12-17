package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.ShapeWatcherConfig
import org.mimirdb.lenses.Lenses

object DatasetShapeWatcher
  extends LensCommand
{ 
  def lens = Lenses.shapeWatcher
  def name: String = "Dataset Stabilizer"
  def lensParameters: Seq[Parameter] = Seq(
    StringParameter(id = "config", name = "Output", hidden = true, default = Some("null"))
  )
  def lensFormat(arguments: Arguments): String = 
    s"WATCH FOR CHANGES"+(
      Json.parse(arguments.get[String]("config")) match {
        case JsNull => ""
        case x => 
          "EXPECTING ("+
            x.as[ShapeWatcherConfig]
             .facets
             .map { "\n"+_.description }
             .mkString(",")+")"
      }
    )

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
    Json.parse(arguments.get[String]("config"))
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = 
    Map( "config" -> JsString(lensArgs.toString()) )
}