/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
    DecimalParameter(id = "sample_rate", default = Some(0.1), required = false, name = "Sampling Rate (0.0-1.0)"),
    StringParameter(id = "output_dataset", required = false, name = "Output Dataset"),
    StringParameter(id = "seed", hidden = true, required = false, default = None, name = "Sample Seed")
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.pretty("sample_rate")} SAMPLE OF ${arguments.get[String]("input_dataset")}"+
    (if(arguments.contains("output_dataset")) {
      s" AS ${arguments.pretty("output_dataset")}"
    } else { "" })
  def title(arguments: Arguments): String = 
    arguments.getOpt[String]("output_dataset")
             .map { output => s"Sample from ${arguments.pretty("input_dataset")} into $output" }
             .getOrElse { s"Sample from ${arguments.pretty("input_dataset")}" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val inputName = arguments.get[String]("input_dataset")
    val outputName = arguments.getOpt[String]("output_dataset")
                              .getOrElse { inputName }
    val probability = arguments.get[Float]("sample_rate")
    val seed = arguments.getOpt[String]("seed").map { _.toLong }

    val input = context.dataset(inputName)
                       .getOrElse { throw new IllegalArgumentException(s"No such dataset $inputName")}
    val (output, _) = context.outputDataset(outputName)

    val response = CreateSampleRequest(
      source = input,
      samplingMode = Uniform(probability),
      seed = seed,
      resultName = Some(output),
      properties = None
    ).handle

    context.updateArguments("seed" -> response.seed.toString)

    context.message("Sample created")
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String]("input_dataset")), 
           Seq(arguments.getOpt[String]("output_dataset")
                        .getOrElse { arguments.get[String]("input_dataset") })) )


}

