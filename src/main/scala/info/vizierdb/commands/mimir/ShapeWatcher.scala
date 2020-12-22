package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.ShapeWatcherConfig
import org.mimirdb.lenses.Lenses

object ShapeWatcher
  extends LensCommand
{ 
  def lens = Lenses.shapeWatcher
  def name: String = "Dataset Stabilizer"
  def lensParameters: Seq[Parameter] = Seq(
    StringParameter(id = "config", name = "Output", required = false, hidden = true, default = Some(""))
  )
  def lensFormat(arguments: Arguments): String = 
    s"WATCH FOR CHANGES"+(
      Json.parse(arguments.get[String]("config")) match {
        case JsNull => ""
        case x => 
          " EXPECTING ("+
            x.as[ShapeWatcherConfig]
             .facets
             .map { "\n  "+_.description }
             .mkString(",")+"\n)"
      }
    )

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
    arguments.get[JsValue]("config") match {
      case null => JsNull
      case JsNull => JsNull
      case x => Json.parse(x.as[String])
    }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = 
    Map( "config" -> JsString(lensArgs.toString()) )
}