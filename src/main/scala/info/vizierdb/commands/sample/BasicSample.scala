package info.vizierdb.commands.sample

import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.request.CreateSampleRequest
import org.mimirdb.api.request.Sample.Uniform

object BasicSample extends Command
  with LazyLogging
{
  def name: String = "Basic Sample"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "input_dataset", name = "Input Dataset"),
    DecimalParameter(id = "sample_rate", default = Some(0.1), name = "Sampling Rate (0.0-1.0)"),
    StringParameter(id = "output_dataset", required = false, name = "Output Dataset"),
    StringParameter(id = "seed", hidden = true, required = false, default = None, name = "Sample Seed")
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.pretty("sample_rate")} SAMPLE OF ${arguments.pretty("input_dataset")} AS ${arguments.pretty("output_dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val inputName = arguments.get[String]("input_dataset")
    val outputName = arguments.getOpt[String]("output_dataset")
                              .getOrElse { inputName }
    val probability = arguments.get[Float]("sample_rate")
    val seed = arguments.get[String]("seed").toLong

    val input = context.dataset(inputName)
                       .getOrElse { throw new IllegalArgumentException(s"No such dataset $inputName")}
    val (output, _) = context.outputDataset(outputName)

    val response = CreateSampleRequest(
      source = input,
      samplingMode = Uniform(probability),
      seed = None,
      resultName = Some(output),
      properties = None
    ).handle

    context.updateArguments("seed" -> response.seed.toString)

    context.message("Sample created")
  }
}