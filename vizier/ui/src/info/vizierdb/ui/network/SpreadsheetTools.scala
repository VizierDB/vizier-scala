package info.vizierdb.ui.network

import info.vizierdb.types._
import info.vizierdb.ui.Vizier
import info.vizierdb.serialized.CommandArgument
import play.api.libs.json._

object SpreadsheetTools
{
  val SPREADSHEET_PACKAGE = "data"
  val SPREADSHEET_COMMAND = "spreadsheet"

  // Keep these constants up to date with backend's info.vizierdb.commands.data.SpreadsheetCommand    
  val PARAM_INPUT = "input"
  val PARAM_SPREADSHEET = "spreadsheet"
  val PARAM_OUTPUT = "output"
  val PARAM_RESULT_DS = "result_ds_id"

  def insertNewSpreadsheet(datasetName: String, position: Int): Unit =
  {
    Vizier.project.now.get
          .branchSubscription.get
          .Client
          .workflowInsert(position, SPREADSHEET_PACKAGE, SPREADSHEET_COMMAND, 
            arguments = Seq(
              CommandArgument(PARAM_INPUT, JsString(datasetName)),
              CommandArgument(PARAM_SPREADSHEET, JsNull),
            )
          )    
  }

  def appendNewSpreadsheet(datasetName: String): Unit =
  {
    Vizier.project.now.get
          .branchSubscription.get
          .Client
          .workflowAppend(SPREADSHEET_PACKAGE, SPREADSHEET_COMMAND, 
            arguments = Seq(
              CommandArgument(PARAM_INPUT, JsString(datasetName)),
              CommandArgument(PARAM_SPREADSHEET, JsNull),
            )
          )
  }
}