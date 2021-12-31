package info.vizierdb.commands.sample

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import info.vizierdb.types._
import info.vizierdb.spark._


case class Uniform(probability:Double) extends SamplingMode
{
  override def toString = s"WITH PROBABILITY $probability"

  def apply(df: DataFrame, seed: Long): DataFrame = 
    df.sample(probability, seed)
}
object Uniform
{
  implicit val format: Format[Uniform] = Json.format
}


/** 
 * Generate a sample of the dataset stratified on the specified column
 * 
 * A stratified sample allows sampling with variable rates depending on 
 * the value of the specified column.  Its most frequent use is to ensure
 * fairness between strata, regardless of their distribution in the 
 * original dataset.  For example, this could be used to derive a sample
 * of demographic data with equal representations from all ethnicities, 
 * even if one ethnicity is under-represented.
 * 
 * Sampling rate is given as a probability.  The final sample will 
 * contain approximately `strata(value) * count(df.col = value)` records
 * where `df.col = value`.  
 *
 * @param    column    The column to use to determine the sampling rate
 * @param    strata    A map from a value for [[column]] to the probability of 
 *                     sampling the value. Non-specified values will not be 
 *                     included in the sample.
 **/
case class StratifiedOn(column:String, strata:Seq[(JsValue,Double)]) extends SamplingMode
{
  override def toString = s"ON $column WITH STRATA ${strata.map { case (v,p) => s"$v -> $p"}.mkString(" | ")}"

  def apply(df: DataFrame, seed: Long): DataFrame = 
  {
    val t = df.schema.fields.find { _.name.equals(column) }.get.dataType
    df.stat.sampleBy(
      column, 
      strata.map { stratum => 
        SparkPrimitive.decode(stratum._1, t) -> stratum._2 
      }.toMap, 
      seed
    )
  }

  def toJson: JsValue = Json.obj(
    "mode" -> JsString(StratifiedOn.MODE),
    "column" -> JsString(column),
    "strata" -> JsArray(
      strata
        .map { case (v, p) => Json.obj(
            "value" -> v,
            "probability" -> JsNumber(p)
          )
        }
    )
  )
}

object StratifiedOn
{
  val MODE = "stratified_on"

  implicit val format = Format[StratifiedOn](
    new Reads[StratifiedOn] { 
      def reads(j: JsValue) =
        JsSuccess(StratifiedOn(
          (j \ "column").as[String],
          (j \ "strata")
            .as[Seq[Map[String,JsValue]]]
            .map { stratum => 
              stratum("value") -> stratum("probability").as[Double]
            }
        ))
    },
    new Writes[StratifiedOn] {
      def writes(j: StratifiedOn) = j.toJson
    }
  )
}


sealed trait SamplingMode
{
  def apply(df: DataFrame, seed: Long): DataFrame
}
object SamplingMode
{
  implicit val format: Format[SamplingMode] = Json.format
}


case class SampleConstructor(
  seed: Long,
  mode: SamplingMode,
  input: Identifier
) extends DataFrameConstructor 
  with DefaultProvenance
{
  def construct(context: Identifier => DataFrame): DataFrame =
    mode(context(input), seed)

  def dependencies = Set(input)
}

object SampleConstructor
{
  implicit val format: Format[SampleConstructor] = Json.format
}