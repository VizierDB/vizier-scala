package info.vizierdb.commands.data

import info.vizierdb.commands.Command
import info.vizierdb.commands.Parameter
import info.vizierdb.commands.Arguments
import info.vizierdb.commands.{Arguments, ExecutionContext}
import info.vizierdb.commands.Arguments
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject
import info.vizierdb.commands.DatasetParameter
import info.vizierdb.commands.CodeParameter
import play.api.libs.json.Json
import info.vizierdb.commands.StringParameter

object SpreadsheetCommand extends Command
{
  val PARAM_INPUT = "input"
  val PARAM_SPREADSHEET = "spreadsheet"
  val PARAM_OUTPUT = "output"

  val DEFAULT_OUTPUT = "spreadsheet"

  override def name: String = "Spreadsheet"

  override def parameters = Seq[Parameter](
    DatasetParameter(id = PARAM_INPUT, name = "Source", required = false),
    CodeParameter(id = PARAM_SPREADSHEET, name = "Spreadsheet", language = "spreadsheet", required = true, hidden = true),
    StringParameter(id = PARAM_OUTPUT, name = "Output", required = false)
  )

  override def format(arguments: Arguments): String = 
    arguments.getOpt[String](PARAM_OUTPUT)
             .map { out => s"Create Spreadsheet $out" }
             .orElse { 
              arguments.getOpt[String](PARAM_INPUT)
                       .map { in => s"Spreadsheet on $in" }
             }
             .getOrElse { s"Create Spreadsheet $DEFAULT_OUTPUT" }

  override def title(arguments: Arguments): String = 
    format(arguments)

  override def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val code = arguments.get[String](PARAM_SPREADSHEET)
    val spreadsheet = Json.parse(code)
    context.message(spreadsheet.toString())
  }

  override def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction = 
    ProvenancePrediction
      .definitelyReads(arguments.getOpt[String](PARAM_INPUT).toSeq:_*)
      .definitelyWrites(
        arguments.getOpt[String](PARAM_OUTPUT)
                 .orElse { 
                    arguments.getOpt[String](PARAM_INPUT)
                 }
                 .getOrElse(DEFAULT_OUTPUT)
      )
      .andNothingElse


}