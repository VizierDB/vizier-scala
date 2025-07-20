/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.ui.network

import info.vizierdb.types._
import info.vizierdb.ui.Vizier
import info.vizierdb.serialized.CommandArgument
import play.api.libs.json._
import info.vizierdb.util.Logging

object SpreadsheetTools extends Logging
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
    logger.trace(s"Inserting spreadsheet for $datasetName @ $position")    
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
    logger.trace(s"Appending spreadsheet for $datasetName")    
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