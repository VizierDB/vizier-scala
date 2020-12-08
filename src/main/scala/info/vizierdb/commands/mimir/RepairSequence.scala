package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.MissingKeyLensConfig

object RepairSequence 
  extends LensCommand
{
  def name = "Repair Sequence"
  def lens = "missing_key"

  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    IntParameter(id = "low", name = "Low Value (optional)", required = false),
    IntParameter(id = "high", name = "High Value (optional)", required = false),
    IntParameter(id = "step", name = "Step Size (optional)", required = false),
  )

  def lensFormat(arguments: Arguments): String = 
    s"FIX SEQUENCE ON COLUMN ${arguments.get[Int]("column")}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue = 
  {
    val low = arguments.getOpt[Long]("low")
    val high = arguments.getOpt[Long]("high")
    val step = arguments.getOpt[Long]("step")
    val field = schema(arguments.get[Int]("column"))

    if(low.isDefined && high.isDefined && step.isDefined){
      Json.toJson(MissingKeyLensConfig(key = field.name, t = field.dataType, 
        low = low.get, high = high.get, step = step.get
      ))
    } else {
      JsString(field.name)
    }
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] =
  {
    val arguments = lensArgs.as[MissingKeyLensConfig]
    Map(
      "low"  -> JsNumber(arguments.low),
      "high" -> JsNumber(arguments.high),
      "step" -> JsNumber(arguments.step)
    )
  }
}
