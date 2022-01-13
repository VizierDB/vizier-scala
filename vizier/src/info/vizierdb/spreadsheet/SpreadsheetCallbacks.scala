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
  def refreshRows(from: Long, count: Int)

  /**
   * Indicates that the specified cells (row, column) have potentially changed content
   */
  def refreshCells(cells: Seq[(Long, Int)])
}