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
import info.vizierdb.spreadsheet.Spreadsheet.UpdateId


class UpdateIndex
{
  val updates = mutable.Map[UpdateId, (UpdatePattern, mutable.Map[ColumnRef, RangeSet])]()
  val cellIndex        = mutable.Map[ColumnRef, RangeMap[UpdatePattern]]()
  val downstreamIndex  = mutable.Map[ColumnRef, RangeMap[Set[UpdateId]]]()

  /**
   * Add a new column to the index
   * @param  col   The added column
   */
  def addColumn(col: ColumnRef)
  {
    cellIndex.put(col, new RangeMap())
    downstreamIndex.put(col, new RangeMap())
  }

  /**
   * Delete a column from the index
   * @param  col   The deleted column
   * @return       A collection of invalidated column references.
   */
  def deleteColumn(col: ColumnRef) =
  {
    // Clean up after the column
    clear(col, cellIndex(col).keys)
    cellIndex.remove(col)
    downstreamIndex.remove(col)
  }

  /**
   * Insert a new pattern into the index
   * @param   pattern     The pattern to insert
   * @param   col         The column to insert the pattern into
   * @param   rows        The rows to insert the pattern into
   * @return              A collection of invalidated column references.
   */
  def put(pattern: UpdatePattern, col: ColumnRef, rows: RangeSet): Unit =
  {
    // Clean up anything that was previously in the cell
    clear(col, rows)

    // Update the mapping from updateId to the cells that contain it
    if(!(updates contains pattern.id))
    { // Case 1: We don't know about pattern.id at all (common case)
      updates(pattern.id) = (pattern, mutable.Map())
    } else if(!(updates(pattern.id)._2 contains col))
    { // Case 2: pattern.id's first occurrence in col
      updates(pattern.id)._2(col) = rows
    } else 
    { // Case 3: pattern.id already exists in col
      updates(pattern.id)._2(col) = updates(pattern.id)._2(col) ++ rows
    }

    // Update the cell index itself
    cellIndex(col).insert(rows, pattern)

    // Figure out what cells are upstream of the inserted ones
    // so that we can update the downstream index.
    for( (upstreamCol, upstreamRows) <- pattern.upstreamCells(rows))
    {
      downstreamIndex(upstreamCol)
        .update(upstreamRows) { 
          case None => Some(Set(pattern.id))
          case Some(others) => Some(others + pattern.id)
        }
    }
  }

  /** 
   * Remove all updates in the specified column:range.
   */
  def clear(col: ColumnRef, rows: RangeSet): Unit =
  {
    // This function has one pre-condition:
    // 1. downstreamIndex is correct, given the value of updates.
    // This function has three post-conditions:
    // 1. No value of `updates` contains col:rows in its range definition
    // 2. cellIndex(col)(rows) is empty
    // 3. downstreamIndex is garbage collected so that downstream() does 
    //    not return a reference in col:rows.

    // Handle step 2 first, since this helps us to ID the targets for
    // step 1 that we also need to update.
    val deletedPatternIds = cellIndex(col).slice(rows)     // Cut out the rows
                                          .map { _._3.id } // 'pattern' is field 3
                                          .toSet           // Only unique pattern IDs

    // Steps 1 and 3 next, together.  For each deleted pattern id, 
    // update `updates`, and then propagate those changes to 
    // downstreamIndex.  
    //
    // Admittedly, we could be a lot more clever about how we update the 
    // downstreamIndex, but in the interest of correctness first, we're
    // going to just delete the pattern entirely from downstreamIndex, 
    // and then re-insert it as needed.
    // 
    // In particular, figuring out whether the pattern needs to be
    // garbage collected from a particular upstreamCell element is
    // insanely hard, given that the upstream could include direct
    // cell references.  
    for( deletedId <- deletedPatternIds )
    {
      // Get the location map
      val (theDeletedPattern, 
           rangesWhereTheDeletedPatternIsUsed) = 
              updates(deletedId)

      // Step 3, part A: Delete the pattern from the downstreamIndex
      // Postcondition here: pattern.id appears in no value in the
      // downstream index
      // Fortunately, OffsetCells make no distinction between 
      // column IDs, so we can just coalesce all of the ranges together
      val allRowsWhereTheDeletedPatternIsUsed =
        rangesWhereTheDeletedPatternIsUsed
          .values
          .foldLeft(RangeSet()) { _ ++ _ }

      val allRangesUpstreamOfTheDeletedPattern =
        theDeletedPattern.upstreamCells(allRowsWhereTheDeletedPatternIsUsed)

      for( (upstreamCol, upstreamRows) <- allRangesUpstreamOfTheDeletedPattern )
      {
        // Remove the pattern from any elements of downstreamIndex
        // where it appears
        downstreamIndex(upstreamCol).update(upstreamRows) {
          case None => None
          case Some(patterns) => 
            val updatedPatterns = patterns - deletedId
            if(updatedPatterns.isEmpty){ None }
            else { Some(updatedPatterns) }
        }
      }

      // Step 1 next.  Update the pattern

      // Find the rows for this pattern (this should not be empty)
      val rowsWhereTheDeletedPatternIsUsedInTheColumnWeCareAbout = 
        rangesWhereTheDeletedPatternIsUsed(col)

      // Figure out the new set of rows in which this pattern is defined
      val rowsWhereThePatternIsUsedAfterDeletion = 
        rowsWhereTheDeletedPatternIsUsedInTheColumnWeCareAbout -- rows

      // If we deleted the last row in this column, remove the column
      // from the usage map
      if(rowsWhereThePatternIsUsedAfterDeletion.isEmpty)
      {
        rangesWhereTheDeletedPatternIsUsed.remove(col)  
        
        // And it that's the last column in the usage map, then
        // just outright delete the pattern.
        if(rangesWhereTheDeletedPatternIsUsed.isEmpty)
        {
          updates.remove(deletedId)
        }
      } else {
        // If we only deleted a subset of the rows, then replace
        // the map entry with whatever remains.
        rangesWhereTheDeletedPatternIsUsed(col) = 
          rowsWhereThePatternIsUsedAfterDeletion
      }

      // If the update has not been removed, then part B of step 3...
      // plug the pattern back into the upstreamIndex
      if(!rangesWhereTheDeletedPatternIsUsed.isEmpty)
      {
        val allRowsWhereTheDeletedPatternIsUsedAfterUpdate =
          rangesWhereTheDeletedPatternIsUsed
            .values
            .foldLeft(RangeSet()) { _ ++ _ }

        val allRangesUpstreamOfTheDeletedPatternAfterUpdate =
          theDeletedPattern.upstreamCells(allRowsWhereTheDeletedPatternIsUsedAfterUpdate)

        for( (upstreamCol, upstreamRows) <- allRangesUpstreamOfTheDeletedPatternAfterUpdate )
        {
          // Remove the pattern from any elements of downstreamIndex
          // where it appears
          downstreamIndex(upstreamCol).update(upstreamRows) {
            case None           => Some(Set(deletedId))
            case Some(patterns) => Some(patterns + deletedId)
          }
        }
      }

    }
  }

  /**
   * Retrieve the pattern at a given cell
   */
  def get(col: ColumnRef, row:Long): Option[UpdatePattern] =
    cellIndex(col)(row)

  /**
   * Retrieve the patterns and ranges in a given range
   */
  def getRanges(col: ColumnRef, rows:RangeSet): Seq[(Long, Long, UpdatePattern)] =
    cellIndex(col)(rows)

  /**
   * Find the set of all cells that are downstream of the specified
   * cells
   */
  def downstream(col: ColumnRef, rows: RangeSet): Map[ColumnRef, RangeSet] =
  {
    downstreamIndex(col)(rows)
      // The index is going to give us a collection of triples of the form:
      //   (from,to) -> { dependency, dependency, ... }
      // Start by inverting the list to map from dependency -> (from,to)
      // for each dependency.
      // 
      // Note that (from,to) here is the range of the upstream cell(s)
      .flatMap { case (from, to, dependencies) => 
        dependencies.map { _ -> RangeSet(from, to) }
      }
      // Group ranges by dependency -> { (dependency, {RangeSets} ) }
      .groupBy { _._1 }
      // Extract and coalesce the RangeSets for each dependency
      .mapValues { _.map { _._2 }.foldLeft(RangeSet()){ _ ++ _ } }
      // Now we have, for each dependency, the set of relevant rows in col
      // that were invalidated.  Translate these into the column/range
      // of the cells that actually get invalidated
      .flatMap { case (dependency, upstreamRows) => 

        // Once we're here, dependency is the id of an update pattern
        // and upstreamRanges is the subset of [rows] that feed into
        // the pattern on one of the rows that contain it.

        // Look up the pattern and find the range of cells that currently
        // contain the pattern
        val (pattern, rangeOfCellsContainingThePattern) = 
          updates(dependency)

        // Ignore the column for the moment, instead, take just the 
        // rows that contain the pattern, and use the pattern to figure
        // out which subset of them is affected by upstreamRows
        rangeOfCellsContainingThePattern.mapValues { downstreamRows =>
          pattern.recomputedSlice(
            downstreamRows,
            col,
            upstreamRows,
          )
        }.toSeq:Seq[(ColumnRef, RangeSet)]
      }
      // As before, coalesce affected cells by column.
      .groupBy { _._1 }
      .mapValues { _.map { _._2 }.foldLeft(RangeSet()){ _ ++ _ } }
  }
}