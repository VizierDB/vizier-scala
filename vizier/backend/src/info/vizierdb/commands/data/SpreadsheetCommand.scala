package info.vizierdb.commands.data

import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json._
import info.vizierdb.spreadsheet.EncodedSpreadsheet
import info.vizierdb.spreadsheet.SpreadsheetDatasetConstructor

object SpreadsheetCommand extends Command
{
  // Keep these constants up to date with ui's info.vizierdb.ui.components.editors.SpreadsheetModuleSummary
  val PARAM_INPUT = "input"
  val PARAM_SPREADSHEET = "spreadsheet"
  val PARAM_OUTPUT = "output"
  val PARAM_RESULT_DS = "result_ds_id"

  val DEFAULT_OUTPUT = "spreadsheet"

  override def name: String = "Spreadsheet"

  override def parameters = Seq[Parameter](
    DatasetParameter(id = PARAM_INPUT, name = "Source", required = false),
    JsonParameter(id = PARAM_SPREADSHEET, name = "Spreadsheet", required = true, hidden = true),
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
    format(arguments)

  def nameHeuristic(output: Option[String], input: Option[String]): String =
    output.filterNot { _ == "" }
          .orElse { input }
          .getOrElse { DEFAULT_OUTPUT }

  override def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val spreadsheet = arguments.getOpt[EncodedSpreadsheet](PARAM_SPREADSHEET)
    if(spreadsheet.isDefined) {
      context.message(spreadsheet.get.toString())
    } else {
      context.message("No Spreadsheet")
    }

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