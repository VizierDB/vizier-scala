package info.vizierdb.spreadsheet

import scala.collection.mutable
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.Expression
import info.vizierdb.spark.vizual.AllRows

/**
 * This class encodes the stucture of a set of updates as an overlay on top of
 * an underlying dataset.  It simply concerns itself with <b>storing</b> the
 * DAG, and not on actually doing anything with it.  Likewise, it does not
 * concern itself with the semantics of structure, things like Column order,
 * data types, or the correctness of expressions in the DAG.
 * 
 * As a result, this class is not meant to work alone.  See [[Executor]] for 
 * the next layer up.
 * 
 * In its simplest form, a single [[UpdateRule]] encodes the value of a single
 * cell, which may be dependent on specific values derived from specific other 
 * cells.  The value of the cell may be specified in terms of a Spark 
 * [[Expression]], which may in turn refer to other cells in the data table by
 * column/row index pairs.
 * 
 * Unfortunately, if we want to allow updates that span entire columns, or 
 * large segments of a dataset, this will lead to a number of cells/computations
 * that scales linearly in the number of records.  We don't want that.  Instead,
 * a [[UpdateRule]] may also be specified declaratively:
 *  - The update may target a range of rows
 *  - References to other rows may be specified as relative offsets (e.g., the
 *    immediately preceding/following row).  Specific cell references are still
 *    allowed, but will have the same reference for all updated rows (analogous
 *    to the $ modifier for row/column references in most spreadsheets)
 * Such updates are generally expected to be specified by a single user 
 * interaction, such as for example copying a formula across multiple cells, or 
 * an entire column.  This way, the number of [[UpdateRule]] objects scales with
 * the number of user interactions, rather than the number of rows of data.  The
 * former could technically grow as big as the larger, but for large datasets
 * this seems less likely.
 * 
 * While we assume that the number of rows of data is large, we're going to 
 * assume that the number of columns is comparatively small.  As a result, we're
 * going to be ok with cloning [[UpdateRule]] objects that need to span multiple
 * columns (for now at least). 
 * 
 * At any rate, coalescing cells together carries withi it several challenges.
 *  - First, efficiently determining which update (if one exists) computes a 
 *    specific cell value poses a challenge. 
 *  - Second, if we change a cell, we need to be able to efficiently identify
 *    which (if any) other [[UpdateRules]] depend on the updated cell.
 *  - Third, a [[UpdateRule]] is unlikely to be overwritten all at once.  
 *    Rather, different parts of it are going to be overwritten a little at a
 *    time.  We need a way to efficiently detect when all of the references to a
 *    specific [[UpdateRule]] have been overwritten.
 *  - Finally, "relative offset"s have to be defined relative to a specific
 *    [[FrameOfReference]] (a bidirectional mapping from a row to its position
 *    in the table).  
 * 
 * We address the first and second challenges by indexing: We maintain an 
 * "lvalueIndex" structure for each column, a range index that stores the 
 * specific [[UpdateRule]] that belongs at that position.  Likewise, we maintain
 * an "rvalueIndex" structure for each column that maintains, for each range of
 * row indices, which cells depend on it.
 * 
 * The third challenge is maintained by instrumenting the lvalueIndex.  Whenever
 * the index deletes (a fragment of) a [[UpdateRule]]'s targetted range, we 
 * react accordingly by flushing out any affected entries in the rvalueIndex.
 * 
 * Finally, we track frames of reference, and tag each [[UpdateRule]] with the
 * frame of reference it was instantiated into.  For now, we only allow simple
 * translations between frames of reference (insert, delete, move rows) that
 * admit efficiently computable arithmetic mappings between frames of reference.  
 */
class UpdateDAG
{

  val lvalueIndex = mutable.Map[ColumnRef, RangeMap[UpdateRule]]()
  val rvalueIndex = mutable.Map[ColumnRef, RangeMap[Seq[UpdateRule]]]()
  val defaults = mutable.Map[ColumnRef, UpdateRule]()

  // we maintain two reference frames, one for cell activation and one for 
  // cell deletion.  Generally these are the same, but during a transition from
  // one frame to another we want to ensure that updates are applied to the 
  // right reference frames
  var frame = ReferenceFrame(Seq.empty)

  var activateCellBuffer: Option[mutable.ArrayBuffer[(Long, Long, UpdateRule)]] = None

  var insertId = 0l

  def addColumn(name: ColumnRef): Unit =
  {
    assert(!lvalueIndex.contains(name))
    lvalueIndex(name) = new RangeMap[UpdateRule]() {
      override def onInsert(from: Long, to: Long, cell: UpdateRule) = 
        activateCell(from, to, cell)
      override def onRemove(from: Long, to: Long, cell: UpdateRule) = 
        deactivateCell(from, to, cell)
    }
    rvalueIndex(name) = new RangeMap[Seq[UpdateRule]]()
  }

  def deleteColumn(name: ColumnRef): Unit =
  {
    // GC all of the cells in the deleted column
    for( (from, (to, cell)) <- lvalueIndex(name).data ){ deactivateCell(from, to, cell) }
    lvalueIndex.remove(name)
    rvalueIndex.remove(name)
  }


  def update(target: LValue, expression: Column, id: Long): Unit =
    update(target, expression.expr, id)

  def update(target: LValue, expression: Expression, id: Long): Unit =
    update(target, UpdateRule(expression, frame, id))

  /**
   * Insert an UpdateRule into the structure.
   * @param   target    The range to insert the rule for
   * @param   rule      The [[UpdateRule]] to insert.
   * 
   * The assumption here is that an `update` is defined relative to the output 
   * reference frame.  This means we shouldn't have any updates relative to 
   * InsertedRows.
   */
  def update(target: LValue, rule: UpdateRule): Unit =
    target match {
      case SingleCell(col, idx) => 
        lvalueIndex(col).insert(idx, idx, rule)
      case ColumnRange(col, from, to) => 
        lvalueIndex(col).insert(from, to, rule)
    }

  def get(column: ColumnRef, idx: Long): Option[UpdateRule] =
    lvalueIndex(column)(idx)
      .orElse { defaults.get(column) }

  def getDependents(column: ColumnRef, rows: RangeSet): Seq[(ColumnRef, RangeSet, UpdateRule)] =
  {
    val index = rvalueIndex(column)
    rows.flatMap { case (searchFrom, searchTo) => 
          // println(s" ... searching $column: $searchFrom -> $searchTo")
          index(searchFrom, searchTo)
            .flatMap { case (from, to, updates) =>
              // println(s"     ... found $from -> $to")
              updates.map { u => 
                (column, u.triggeredRanges(from, to, column, frame), u) }
            }
        }.toSeq
  }
  def getFlatDependents(column: ColumnRef, rows: RangeSet): Seq[(ColumnRef, Long, Long, UpdateRule)] =
    getDependents(column, rows)
      .flatMap { case (depCol, depRows, depUpdate) => 
        depRows.map { case (from, to) => (depCol, from, to, depUpdate) }
      }


  /**
   * Update the map's frame of reference to account for deletion of count rows at idx
   */
  def deleteRows(idx: Long, count: Int) =
  {
    val updatesForReexecution = mutable.ArrayBuffer[(RangeSet, UpdateRule)]()
    deferCellActivation { 
      for( column <- lvalueIndex.values ){
        column.collapse(idx, count)
      }
      for( (name, column) <- rvalueIndex ){
        for( (deleteFrom, deleteTo, deletedUpdates) <- column.collapse(idx, count) ){
          for( deletedUpdate <- deletedUpdates ){
            updatesForReexecution.append( (
              deletedUpdate.triggeredRanges(deleteFrom, deleteTo, name, frame), 
              deletedUpdate
            ) )
          }
        }
      }
      frame = frame + DeleteRows(idx, count)
    }
  }
  /**
   * Update the map's frame of reference to account for insertion of count rows at idx
   */
  def insertRows(idx: Long, count: Int) = 
  {
    deferCellActivation { 
      for( column <- lvalueIndex.values ){
        column.expand(idx, count)
      }
      for( column <- rvalueIndex.values ){
        column.expand(idx, count)
      }
      frame = frame + InsertRows(idx, count, insertId)
      insertId += 1
    }
  }
  /**
   * Update the map's frame of reference to account for a move of count rows from from to to
   */
  def moveRows(from: Long, to: Long, count: Int) =
  {
    deferCellActivation( {
      for( column <- lvalueIndex.values ){
        column.move(from, to, count)
      }
      for( column <- rvalueIndex.values ){
        column.move(from, to, count)
      }
      frame = frame + MoveRows(from, to, count)
    })
  }

  def deferCellActivation[T](op: => T): T =
  {
    activateCellBuffer = Some(mutable.ArrayBuffer())
    val ret = op
    val activatedCells = activateCellBuffer.get
    activateCellBuffer = None
    for( (from, to, cell) <- activatedCells ){
      activateCell(from, to, cell)
    }
    return ret
  }

  def activateCell(from: Long, to: Long, cell: UpdateRule): Unit =
  {
    if(activateCellBuffer.isDefined){
      activateCellBuffer.get.append( (from, to, cell) )
      return
    }
    // println(s"ACTIVATE: $cell on $from -> $to")
    for( (columnName, ranges) <- cell.triggeringRanges(from, to, frame) )
    {
      // println(s"Affected by $columnName, $ranges")
      val column = rvalueIndex(columnName)
      for( (refFrom, refTo) <- ranges ){
        for( (insertFrom, insertTo, otherUpdates) <- 
                RangeMap.fillGaps(refFrom, refTo, column.slice(refFrom, refTo)) )
        {
          // println(s"Insert @ $insertFrom -> $insertTo")
          column.insert(insertFrom, insertTo, cell +: otherUpdates.getOrElse(Seq.empty))
        }

      }
    }
  }

  def deactivateCell(from: Long, to: Long, cell: UpdateRule) =
  {
    for( (columnName, ranges) <- cell.triggeringRanges(from, to, frame) )
    {
      val column = rvalueIndex(columnName)
      for( (refFrom, refTo) <- ranges ){
        for( (insertFrom, insertTo, updates) <- column.slice(refFrom, refTo) )
        {
          val updatesWithoutCell = updates.filterNot { _ == cell }
          if(updatesWithoutCell.isEmpty){
            column.slice(insertFrom, insertTo)
          } else {
            column.insert(insertFrom, insertTo, updatesWithoutCell)
          }
        }
      }
    }
  }

}