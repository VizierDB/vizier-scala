package info.vizierdb.commands.sample

import info.vizierdb.commands._
import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.request.CreateSampleRequest
import org.mimirdb.api.request.Sample.StratifiedOn
import org.mimirdb.api.MimirAPI
import org.mimirdb.spark.SparkPrimitive

object ManualStratifiedSample extends Command
  with LazyLogging
{
  def name: String = "Manually Stratified Sample"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "input_dataset", name = "Input Dataset"),
    ColIdParameter(id = "stratification_column", name = "Column"),
    ListParameter(id = "strata", name = "Strata", components = Seq(
      StringParameter(id = "stratum_value", name = "Column Value"),
      DecimalParameter(id = "sample_rate", name = "Sampling Rate (0.0-1.0)"),
    )),
    StringParameter(id = "output_dataset", required = false, name = "Output Dataset"),
    StringParameter(id = "seed", hidden = true, required = false, default = None, name = "Sample Seed")
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.pretty("sample_rate")} SAMPLE OF ${arguments.get[String]("input_dataset")}"+
    s"STRATIFIED ON ${arguments.get[String]("stratification_column")}"+
    (if(arguments.contains("output_dataset")) {
      s" AS ${arguments.pretty("output_dataset")}"
    } else { "" })
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val inputName = arguments.get[String]("input_dataset")
    val outputName = arguments.getOpt[String]("output_dataset")
                              .getOrElse { inputName }
    val seed = arguments.getOpt[String]("seed").map { _.toLong }
    val stratifyOn = arguments.get[String]("stratification_column")
    val strata = arguments.getList("strata")
                          .map { row => 
                            JsString(row.get[String]("stratum_value")) -> 
                              row.get[Double]("sample_rate")
                          }
    val probability = arguments.get[Float]("sample_rate")

    val input = context.dataset(inputName)
                       .getOrElse { throw new IllegalArgumentException(s"No such dataset $inputName")}
    val (output, _) = context.outputDataset(outputName)

    val response = CreateSampleRequest(
      source = input,
      samplingMode = StratifiedOn(stratifyOn, strata),
      seed = seed,
      resultName = Some(output),
      properties = None
    ).handle

    context.updateArguments("seed" -> response.seed.toString)

    context.message("Sample created")
  }
}