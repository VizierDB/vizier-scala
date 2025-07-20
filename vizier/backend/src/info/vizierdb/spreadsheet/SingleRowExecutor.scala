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

import info.vizierdb.VizierException
import scala.collection.mutable
import scala.concurrent.Future
import org.apache.spark.sql.catalyst.expressions.Unevaluable
import org.apache.spark.sql.catalyst.expressions.Literal
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.apache.spark.sql.catalyst.expressions.Expression
import java.util.concurrent.atomic.AtomicLong
import com.typesafe.scalalogging.LazyLogging

/**
 * A limited form of the Executor that only supports formulas on the 
 * current, active row.  This is largely a placeholder until we get
 * full support for converting multi-row formulas into spark.  
 */
class SingleRowExecutor(
  sourceData: (ColumnRef, Long) => Future[Any], 
  onCellUpdate: (ColumnRef, Long) => Unit
) extends LazyLogging
{
  implicit val ec = scala.concurrent.ExecutionContext.global

  type Datum = Any
  type RowIndex = Long
  type ColIndex = Int

  /**
   * All of the updates
   */
  val updates = mutable.Map[ColumnRef, (RangeMap[UpdatePattern], Option[UpdatePattern])]()

  /**
   * Row insertions/deletions
   */
  val sourceRowMapping = new SourceReferenceMap()

  /**
   * A mapping from column to position in the activeCells array
   */
  val columns = mutable.Map[ColumnRef, ColIndex]()

  /**
   * Data for all cells in activeRows
   */
  val activeCells = mutable.Map[RowIndex, mutable.ArrayBuffer[Option[Cell]]]()

  def requiredSourceRows: Seq[RowIndex] =
    activeCells.keys.toSeq

  def subscribe(rows: RangeSet): Unit =
  {
    val colRefs = 
      columns.toSeq.sortBy { _._2 }.map { _._1 }
    assert(columns.values.toSet == (0 until columns.size).toSet)

    // Guard against parallel access to the set of active rows
    synchronized {

      for(row <- rows.indices)
      {
        if( !(activeCells contains row) )
        {
          val data = mutable.ArrayBuffer[Option[Cell]](
            colRefs.map { col =>
              val (byRange, default) = updates(col)

              byRange(row).orElse { default }.map { pattern => 
                new Cell(pattern)
              }
            }.toSeq:_*
          )
          activeCells(row) = data
          recompute( colRefs.map { (_, row) } )
        }
      }
    }
  }

  def unsubscribe(rows: RangeSet): Unit =
  {
    // Guard against parallel access to the set of active rows
    synchronized {
      for(row <- rows.indices)
      {
        activeCells.remove(row)
      }
    }
  }

  def addColumn(col: ColumnRef): Unit = 
  {
    if(columns contains col){
      throw new VizierException(f"$col already exists.")
    }
    val colIdx = columns.size
    columns.put(col, colIdx)
    activeCells.values.foreach { _.append(None) }
    updates.put(col, (new RangeMap(), None))
    assert(columns.map { _._2 }.toSet == (0 until columns.size).toSet)
    logger.trace(activeCells.map { case (row, data) => s"$row -> ${data.mkString("; ")}"}.mkString("\n"))
    assert(activeCells.values.forall { _.size > colIdx })
  }

  def deleteColumn(col: ColumnRef): Unit =
  {
    val idx = columns(col)
    val invalidatedCells = 
      downstream(
        activeCells.toSeq.map { case (row, _) => (col, row) }
      ).filterNot { _._1 == col }

    updates.remove(col)
    columns.remove(col)
    for(k <- columns.keys)
    {
      if(columns(k) > idx){ 
        columns(k) = columns(k) - 1
      }
    }
    activeCells.values.foreach { _.remove(idx, 1) }
    logger.trace(s"Recomputing invalidated cells: ${invalidatedCells.mkString(", ")}")
    recompute(invalidatedCells)
    assert(columns.values.toSet == (0 until columns.size).toSet,
          s"After deletion (idx = $idx), column indices (${columns.values.toSet.toSeq.sorted.mkString(", ")}) differ from buffer [0, ${columns.size}]")
    assert(columns.map { _._2 }.toSet == (0 until columns.size).toSet)
  }

  def deleteRows(position: Long, count: Long): Unit =
    deleteRows(new RangeSet(Seq(position -> (position+count-1))))

  def deleteRows(rows: RangeSet): Unit =
  {
    logger.trace(s"Deleting rows $rows")

    // We don't need to worry about changing offsets and other row references in expressions
    // because all expressions refer solely to their local rows. (in the SingleRowExecutor)
    
    // Guard against parallel access to the set of active rows
    synchronized {
      for( (low, high) <- rows )
      {
        updates.values.foreach { 
          _._1.collapse(low, high-low)
        }
        sourceRowMapping.delete(low, (high-low+1).toInt)
      }
      for(row <- activeCells.keys.toIndexedSeq.sorted)
      {
        if(rows(row)){ /* keep it deleted */
          activeCells.remove(row) 
        } else {
          val rowOffset = rows.countTo(row)
          if(rowOffset > 0){
            val rowData = activeCells.remove(row).get
            // Re-insertion should be safe, since we're traversing the cells in ascending
            // order (see the use of 'sorted' above)
            activeCells.put(row - rowOffset, rowData)
          }
        }
      }
    }
  }

  def insertRows(position: Long, count: Int): Unit =
  {
    logger.trace(s"Inserting $count rows @ $position")

    // We don't need to worry about changing offsets and other row references in expressions
    // because all expressions refer solely to their local rows. (in the SingleRowExecutor)
    
    // Guard against parallel access to the set of active rows
    synchronized {
      updates.values.foreach { 
        _._1.inject(position, count)
      }
      sourceRowMapping.insert(position, count)

      val subscribeToNewRows = activeCells contains position

      for(row <- activeCells.keys.toIndexedSeq.sorted)
      {
        if(row >= position)
        {
          val rowData = activeCells.remove(row).get
          // Re-insertion should be safe, since we're traversing the cells in ascending
          // order (see the use of 'sorted' above)
          activeCells.put(row + count, rowData)
        }
      }

      if(subscribeToNewRows)
      {
        subscribe(RangeSet(position, position+count-1))
      }
    }
  }

  def moveRows(from: Long, to: Long, count: Int): Unit =
  {
    val renumber = 
      if(from < to)
      {
        (x: Long) => if(x < from || x > to){ x }
                    else if(x < from + count){ x - from + to }
                    else { x - count }
      } else {
        (x: Long) => if(x < to || x > from+count){ x }
                    else if(x >= from){ x - from + to }
                    else { x + count }        
      }

    synchronized {
      updates.values.foreach { _._1.move(from, to, count) }
      sourceRowMapping.move(from, to, count)
      val remappedRows = 
        activeCells.keys.toIndexedSeq
                   .flatMap { row => 
                      if(renumber(row) == row) { None }
                      else {
                        Some( renumber(row) -> activeCells.remove(row).get )
                      }
                   }

      for( (row, data) <- remappedRows)
      {
        activeCells.put(row, data)
      }
    }
  }


  def invalidate(cells: Iterable[(ColumnRef, RowIndex)]): Unit =
  {
    val fullDependencies = downstream(cells)
    logger.trace(s"All Dependencies: ${fullDependencies.mkString(", ")}")
    recompute(fullDependencies)
  }

  def downstream(cells: Iterable[(ColumnRef, RowIndex)]): Seq[(ColumnRef, RowIndex)] =
  {
    logger.trace(s"Finding downstream of ${cells.mkString(", ")}")
    logger.trace(s"Currently active rows: ${activeCells.keys.mkString(", ")}")
    val activeCellsByRow = 
      cells.filter { case (col, row) => activeCells contains row }
           .groupBy { _._2 }
           .mapValues { _.map { _._1 } }

    activeCellsByRow.toSeq.flatMap { case (row, cols) => 
      var neededCols = cols.toSet
      var unneededCols = columns.keySet -- cols
      var checkCols: Set[ColumnRef] = cols.toSet
      val data = activeCells(row)

      while(!checkCols.isEmpty)
      {
        val columnsNowNeeded = 
          unneededCols.filter { unneeded => 
            val idx = columns(unneeded)
            data(idx).isDefined &&
              !(data(idx).get.upstream intersect checkCols isEmpty)
          }
          .toSet

        neededCols ++= columnsNowNeeded
        unneededCols --= columnsNowNeeded
        checkCols = columnsNowNeeded
      }

      logger.trace(s"On row $row: Need columns ${neededCols.mkString}")

      neededCols.map { (_, row) }
    }
  } 

  def recompute(cells: Iterable[(ColumnRef, RowIndex)]): Unit =
  {
    logger.trace(s"Preparing to recompute ${cells.mkString(", ")}")

    // Synchronization is required to guard against potential updates to the set of rows
    // (or active rows)
    synchronized {
      val activeCellsByRow = 
        cells.filter { case (col, row) => activeCells contains row }
             .groupBy { _._2 }
             .mapValues { _.map { _._1 }.toSet }

      for( (row, cols) <- activeCellsByRow )
      {
        val data = activeCells(row)
        // visit the datums in topological order
        val todo = mutable.Queue(cols.toSeq:_*)
        val deferred = mutable.Stack[ColumnRef]()
        logger.trace(s"Recomputing ${cols.mkString(", ")} on row $row: $data")

        /**
         * Invariant: deferred and todo are mutually exclusive
         */
        while(!todo.isEmpty || !deferred.isEmpty)
        {
          val current = 
            if(deferred.isEmpty) { todo.dequeue() }
            else { deferred.pop() }

          logger.trace(s"Currently reviewing $current[$row]")

          // Skip columns not defined in the overlay
          if(data(columns(current)).isDefined){
            val cell = data(columns(current)).get
            val deps = cell.upstream

            logger.trace(s"Checking for cycles or uncomputed dependencies in $current[$row]")

            // if the current column's upstream includes something
            // on the deferred queue, that means this column is upstream
            // of that... we have a cycle
            if(! (deps intersect deferred.toSet isEmpty) )
            {
              cell.error("Cyclic dependency")
            } else 
            {

              // if we have any other dependencies, pick one, and handle
              // it first.
              val depsToDo = deps intersect todo.toSet
              if( ! depsToDo.isEmpty )
              {
                val next = depsToDo.head
                deferred.push(current)
                deferred.push(next)
                logger.trace(s"Need to cmpute $next[$row] before $current[$row]")
                todo.dequeueFirst { _ == next }
              } else 
              {
                // no other todos for this node
                cell.recompute(
                  deps.map { case dep =>
                    dep -> getFuture(dep, row)
                  }.toMap
                )
                logger.trace(s"Recomputing $cell")
                onCellUpdate(current, row)
              }
            }
          } else 
          {
            logger.trace(s"Using source data for $current[$row]")
            // we were asked to recompute... **we** don't need to do anything
            // but the following should trigger a new query to the source.

            // Generally, this should only happen when a cell first enters
            // scope.
            onCellUpdate(current, row)
          }
        }
      }
    }
  }

  def getFuture(col: ColumnRef, row: RowIndex): Future[Datum] =
  {
    assert(activeCells contains row, "Can only get active rows")
    val rowData = activeCells(row)
    val colIdx = 
      columns.get(col)
             .getOrElse { 
              return Future.failed(
                new VizierException(s"No such column: $col [${col.id} <- ${columns.keys.map { _.id }.toSeq.sorted.mkString(", ")}]")
              )
             }

    logger.trace(s"RowData @ $col[$row] (idx $colIdx): $rowData")

    rowData(colIdx) match 
    {
      case Some(cell) => cell.data
      case None => 
        sourceRowMapping(row) match {
          case Some(sourceRow) => sourceData(col, sourceRow)
          case None => Future.successful(null)
        }
    }
  }

  def getExpression(col: ColumnRef, row: RowIndex): Option[Expression] =
  {
    assert(activeCells contains row, "Can only get active rows")
    val rowData = activeCells(row)
    val colIdx = columns(col)

    rowData(colIdx).map { _.pattern.expression }
  }

  val nextUpdateIdx = new AtomicLong(0)

  def update(target: LValue, expression: Expression): Unit =
    update(target, new UpdatePattern(expression, nextUpdateIdx.getAndIncrement()))

  def update(target: LValue, pattern: UpdatePattern): Unit =
  {
    logger.trace(s"Column map before update: ${columns}")

    def initCell(col: ColumnRef, row: Long) =
    {
      logger.trace(s"Initializing $col[$row]")
      activeCells.get(row).foreach { rowData => 
        rowData(columns(col)) = 
          Some(new Cell(pattern))
      }
    }

    target match {
      case SingleCell(col, row) =>
        updates(col)._1.insert(row, pattern)
        initCell(col, row)
        invalidate(Seq( (col, row) ))

      case ColumnRange(col, start, end) => 
        updates(col)._1.insert(start, end, pattern)
        for(row <- start to end){ initCell(col, row) }
        invalidate( (start to end).map { (col, _) } )

      case FullColumn(col) => 
        updates(col) = (
          updates(col)._1,
          Some(pattern)
        )
        for(row <- activeCells.keys){ initCell(col, row) }
        invalidate(activeCells.keys.map { (col, _) })
    }
  }

  def colsOfPattern(pattern: UpdatePattern): Set[ColumnRef] =
    pattern.rvalues.collect {
      case OffsetCell(col, 0) => col
    }.toSet

  def loadUpdates(updates: Seq[(UpdatePattern, Seq[LValue])]) =
  {
    for( 
      (pattern, targets) <- updates;
      target <- targets
    ) { update(target, pattern) }
    nextUpdateIdx.set(updates.map { _._1.id }.max + 1)
  }

  class Cell(
    val pattern: UpdatePattern,
    val upstream: Set[ColumnRef],
    var data: Future[Datum]
  )
  {
    def this(pattern: UpdatePattern) =
    {
      this(
        pattern,
        colsOfPattern(pattern),
        Future.successful { null }
      )
    }

    def error(msg: String) =
    {
      data = Future.failed(new VizierException(msg))
    }

    def recompute(row: Map[ColumnRef, Future[Any]])
    {
      data = Future {
        logger.trace(s"Computing ${pattern.expression}")
        pattern.expression.transform
        {
          case RValueExpression(cell@OffsetCell(col, 0)) => 
            logger.trace(s"Retrieving $cell")
            val result = Await.result(row(col), Duration.Inf)
            logger.trace(s"Retrieved $cell: $result")
            Literal(result)
          case RValueExpression(_) => 
            throw new VizierException("Formulas that reference cells outside of the same row are not currently supported.")
          case InvalidRValue(err) => 
            throw new VizierException(err)
          case x:Unevaluable =>
            throw new VizierException(s"The formula '$x' [${x.getClass().getSimpleName()}] is not supported yet.")

        }.eval()
      }
    }

    override def toString: String =
      s"${data.value} <- '=${pattern.expression}'"
  }

}