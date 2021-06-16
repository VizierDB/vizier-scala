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
package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual
import org.mimirdb.api.request.VizualRequest
import com.typesafe.scalalogging.LazyLogging

trait VizualCommand 
  extends Command
  with LazyLogging
{

  val PARA_DATASET = "dataset"

  def vizualParameters: Seq[Parameter]
  def script(arguments: Arguments, context: ExecutionContext): Seq[vizual.Command]

  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PARA_DATASET, name = "Dataset")
  ) ++ vizualParameters

  def title(arguments: Arguments): String =
    s"Vizual ${getClass().getSimpleName()} on ${arguments.pretty(PARA_DATASET)}"


  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String](PARA_DATASET)

    logger.debug(s"${this.getClass().getName()}($arguments) <- $datasetName")

    val input = context.dataset(datasetName)
                         .getOrElse { 
                            throw new IllegalArgumentException(s"No such dataset '$datasetName'")
                         }

    val vizualScript = script(arguments, context)

    val (output, _) = context.outputDataset(datasetName)

    logger.debug(s"${this.getClass().getName()} -> $input -> $output")

    VizualRequest(
      input = input,
      script = vizualScript,
      resultName = Some(output),
      compile = Some(false)
    ).handle

    context.message(s"Updated $datasetName")
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String](PARA_DATASET)), 
           Seq(arguments.get[String](PARA_DATASET))) )


}

