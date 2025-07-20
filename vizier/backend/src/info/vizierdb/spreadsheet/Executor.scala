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

import scala.collection.mutable
import scala.concurrent.Future

class Executor(source: SpreadsheetDataSource)
{
  val index = new UpdateIndex()
  // 
  // Terminology note: 
  // 
  // A is downstream of B == A reads from B
  //   The downstream of A == all cells that read from A (transitively)
  // A is upstream of B == B reads from A
  //   The upstream of A == all cells that A reads from (transitively)

  type Datum = Any
  type RowIndex = Long

  /**
   * The set of rows that are currently in an active state
   */
  var activeRows = RangeSet()

  /**
   * Data for all cells in activeRows
   */
  val activeCells = mutable.Map[ColumnRef, mutable.Map[RowIndex, Option[Future[Datum]]]]()

  /**
   * Data for all cells not in activeRows, but that is an upstream
   * dependency of one or more cells in activeRows.
   * 
   * ._2 is a reference count.
   * 
   * TODO: actually maintain magicCells properly
   */
  val magicCells = mutable.Map[(ColumnRef, Long), (Future[Datum], Int)]()

  def addColumn(col: ColumnRef): Unit = 
  {
    activeCells.put(col, mutable.Map.empty)
  }

  def deleteColumn(col: ColumnRef): Unit = 
  {
    val invalidatedCells = 
      downstream( Map(col -> activeRows) )
        .filterKeys { _ != col }

    val magicKeysToDelete =
      magicCells.keys.filter { _._1 == col }.toIndexedSeq

    for(key <- magicKeysToDelete) { magicCells.remove(key) }
    activeCells.remove(col)

    // need to invalidate downstream dependencies...
    recompute(invalidatedCells)
  }

  /**
   * Mark a specific set of cells as invalid and recompute
   * them and all of their downstream dependencies.
   * @param cells    The set of cells to invalidate
   */
  def invalidate(cells: Map[ColumnRef, RangeSet]): Unit =
    recompute(downstream(cells))

  /**
   * Obtain the transitive closure of cells and their downstreams.
   * @param cells    The set of cells to obtain the closure of
   * @return         The transitive closure of cells and their downstreams
   * 
   * This method returns the contents of cells together with every other
   * cell that has a direct or indirect dependency on a cell in cells.
   */
  def downstream(cells: Map[ColumnRef, RangeSet], limitToActiveRowsAndDirectDependencies: Boolean = true): Map[ColumnRef, RangeSet] = 
  {
    // BFS out the full set of downstream dependencies:
    val seen = mutable.Map[ColumnRef, RangeSet](cells.toSeq:_*)
                         .withDefaultValue { RangeSet() }

    val toVisit = mutable.Queue[(ColumnRef, RangeSet)](cells.toSeq:_*)

    while(!toVisit.isEmpty)
    {
      val (nextCol, nextRows) = toVisit.dequeue
      for( (downstreamCol, downstreamRows) <- index.downstream(nextCol, nextRows) )
      {
        // Only follow downstream rows that are 1-hop dependencies
        // outside of 
        val unseenDownstreamRows = 
          if(limitToActiveRowsAndDirectDependencies)
          {
            (downstreamRows -- seen(downstreamCol)) intersect activeRows
          } else {
            (downstreamRows -- seen(downstreamCol))
          }

        seen(downstreamCol) =
          seen(downstreamCol) ++ downstreamRows

        toVisit.enqueue( downstreamCol -> unseenDownstreamRows )
      }
    }

    return seen.toMap
  }

  /**
   * Force recomputation on a specific set of cells.  
   * 
   * <b>This function should usually not be called directly</b>
   * 
   * It will not trigger re-computation in downstream cells.  Use 
   * [[invalidate]]() instead.
   * 
   * Also note that this function assumes that all values upstream of
   * `cells` is already computed (and valid).  If not, an error will
   * be triggered.
   */
  private def recompute(cells: Map[ColumnRef, RangeSet]) =
  {
    val (activeCellsToCompute, magicCellsToCompute) = 
      cells.flatMap { case (col, ranges) => 
                ranges.indices.map { (col, _) }
            }
           .partition { case (_, row) => activeRows(row) }

    ???
  }



}