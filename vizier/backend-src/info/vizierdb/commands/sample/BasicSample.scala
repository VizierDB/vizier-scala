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
import scala.util.Random
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject

object BasicSample extends Command
  with LazyLogging
{
  val PAR_INPUT_DATASET = "input_dataset"
  val PAR_SAMPLE_RATE = "sample_rate"
  val PAR_OUTPUT_DATASET = "output_dataset"
  val PAR_SEED = "seed"

  def name: String = "Basic Sample"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PAR_INPUT_DATASET, name = "Input Dataset"),
    DecimalParameter(id = PAR_SAMPLE_RATE, default = Some(0.1), required = false, name = "Sampling Rate (0.0-1.0)"),
    StringParameter(id = PAR_OUTPUT_DATASET, required = false, name = "Output Dataset"),
    StringParameter(id = PAR_SEED, hidden = true, required = false, default = None, name = "Sample Seed"),
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.pretty(PAR_SAMPLE_RATE)} SAMPLE OF ${arguments.get[String](PAR_INPUT_DATASET)}"+
    (if(arguments.contains(PAR_OUTPUT_DATASET)) {
      s" AS ${arguments.pretty(PAR_OUTPUT_DATASET)}"
    } else { "" })
  def title(arguments: Arguments): String = 
    arguments.getOpt[String](PAR_OUTPUT_DATASET)
             .map { output => s"Sample from ${arguments.pretty(PAR_INPUT_DATASET)} into $output" }
             .getOrElse { s"Sample from ${arguments.pretty(PAR_INPUT_DATASET)}" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val inputName = arguments.get[String](PAR_INPUT_DATASET)
    val outputName = arguments.getOpt[String](PAR_OUTPUT_DATASET)
                              .getOrElse { inputName }
    val probability = arguments.get[Float](PAR_SAMPLE_RATE)
    val seedMaybe = arguments.getOpt[String](PAR_SEED).map { _.toLong }
    val seed = seedMaybe.getOrElse { Random.nextLong }

    val input = context.artifact(inputName)
                       .getOrElse { throw new IllegalArgumentException(s"No such dataset $inputName")}

    context.message("Registering sample...")
    context.outputDataset(
      outputName,
      SampleConstructor(
        seed = seed,
        Uniform(probability),
        input = input.id
      )
    )

    if(seedMaybe.isEmpty) { 
      context.updateArguments(PAR_SEED -> seed.toString)
    }

    context.message("Sample created")
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PAR_INPUT_DATASET))
      .definitelyWrites(
        arguments.getOpt[String](PAR_OUTPUT_DATASET)
                 .getOrElse { arguments.get[String](PAR_INPUT_DATASET) }
      )
      .andNothingElse


}

