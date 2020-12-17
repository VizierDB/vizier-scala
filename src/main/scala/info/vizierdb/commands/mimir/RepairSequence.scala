package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.MissingKeyLensConfig
import org.apache.spark.sql.types.{ ShortType, IntegerType, LongType }
import org.mimirdb.lenses.Lenses

object RepairSequence 
  extends LensCommand
{
  def name = "Repair Sequence"
  def lens = Lenses.missingKey

  def lensParameters: Seq[Parameter] = Seq(
    TemplateParameters.COLUMN,
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

    field.dataType match {
      case ShortType | IntegerType | LongType => ()
      case _ => 
        throw new IllegalArgumentException(s"The key column should be an integer type and not ${field.dataType.prettyJson}")
    }

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
