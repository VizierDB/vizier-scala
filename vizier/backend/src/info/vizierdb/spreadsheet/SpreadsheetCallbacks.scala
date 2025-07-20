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
package info.vizierdb.spreadsheet


/**
 * A delegate trait for the spreadsheet that signals when specific elements
 * need to be redisplayed/etc...
 */
trait SpreadsheetCallbacks
{
  /**
   * Indicates that the entire dataset is invalid and should be re-read
   */
  def refreshEverything()

  /**
   * Indicates that the header rows have changed content (but not order)
   */
  def refreshHeaders()

  /**
   * Indicates that the entire dataset has (potentially) changed content
   */
  def refreshData()

  /**
   * Indicates that the specified rows have (potentially) changed content
   */
  def refreshRows(from: Long, count: Long)

  /**
   * Indicates that the specified cells (row, column) have potentially changed content
   */
  def refreshCell(column: Int, row: Long)

  /**
   * Indicates that the specified cells (row, column) have potentially changed content
   */
  def sizeChanged(newSize: Long)
}