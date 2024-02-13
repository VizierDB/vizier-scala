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
package info.vizierdb.commands.data

import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json._
import info.vizierdb.spreadsheet.EncodedSpreadsheet
import info.vizierdb.spreadsheet.SpreadsheetDatasetConstructor

object SpreadsheetCommand extends Command
{
  // Keep these constants up to date with ui's info.vizierdb.ui.network.SpreadsheetTools
  val PARAM_INPUT = "input"
  val PARAM_SPREADSHEET = "spreadsheet"
  val PARAM_OUTPUT = "output"
  val PARAM_RESULT_DS = "result_ds_id"

  val DEFAULT_OUTPUT = "spreadsheet"

  override def name: String = "Spreadsheet"

  override def hidden: Boolean = true

  override def parameters = Seq[Parameter](
    DatasetParameter(id = PARAM_INPUT, name = "Source", required = false),
    JsonParameter(id = PARAM_SPREADSHEET, name = "Spreadsheet", default = Some(JsNull), required = false, hidden = true),
    StringParameter(id = PARAM_OUTPUT, name = "Output", required = false),
    CachedStateParameter(id = PARAM_RESULT_DS, name = "Result Dataset", required = false, hidden = true),
  )

  override def format(arguments: Arguments): String = 
    arguments.getOpt[String](PARAM_OUTPUT)
             .map { out => s"Create Spreadsheet $out" }
             .orElse { 
              arguments.getOpt[String](PARAM_INPUT)
                       .map { in => s"$in as Spreadsheet" }
             }
             .getOrElse { s"Create Spreadsheet $DEFAULT_OUTPUT" }

  override def title(arguments: Arguments): String = 
    s"Spreadsheet[${nameHeuristic(arguments.getOpt[String](PARAM_INPUT), arguments.getOpt[String](PARAM_OUTPUT))}]"

  def nameHeuristic(input: Option[String], output: Option[String]): String =
    output.filterNot { _ == "" }
          .orElse { input }
          .getOrElse { DEFAULT_OUTPUT }

  override def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val spreadsheet = arguments.getOpt[EncodedSpreadsheet](PARAM_SPREADSHEET)
    // if(spreadsheet.isDefined) {
    //   context.message(spreadsheet.get.toString())
    // } else {
    //   context.message("No Spreadsheet")
    // }

    val input = arguments.getOpt[String](PARAM_INPUT)
    val output = arguments.getOpt[String](PARAM_OUTPUT)

    if(spreadsheet.isDefined){
      val ds = 
        context.outputDataset(
          name = nameHeuristic(input, output),
          new SpreadsheetDatasetConstructor(input.map { context.artifactId(_).get }, spreadsheet.get)
        )
      context.updateArguments(
        PARAM_RESULT_DS -> JsNumber(ds.id)
      )
    } else if(input.isDefined) {
      context.artifactId(input.get)
    }
  }

  override def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction = 
    ProvenancePrediction
      .definitelyReads(arguments.getOpt[String](PARAM_INPUT).toSeq:_*)
      .definitelyWrites(
        nameHeuristic(
          arguments.getOpt[String](PARAM_INPUT),
          arguments.getOpt[String](PARAM_OUTPUT)
        )
      )
      .andNothingElse
}