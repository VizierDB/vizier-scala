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
  val sourceRowOffsets = new OffsetMap()

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

  def unsubscribe(rows: RangeSet): Unit =
  {
    for(row <- rows.indices)
    {
      activeCells.remove(row)
    }
  }

  def addColumn(col: ColumnRef): Unit = 
  {
    if(columns contains col){
      throw new VizierException(f"$col already exists.")
    }
    columns.put(col, columns.size)
    activeCells.mapValues { 
      _.append(None)
    }
    updates.put(col, (new RangeMap(), None))
    assert(columns.map { _._2 }.toSet == (0 until columns.size).toSet)
  }

  def deleteColumn(col: ColumnRef): Unit =
  {
    val idx = columns(col)
    val invalidatedCells = 
      activeCells.toSeq.flatMap { case (row, data) => 
        if(data(idx).isDefined) { Some( (col, row) ) }
        else { None }
      }
    invalidate(invalidatedCells)
    updates.remove(col)
    columns.remove(col)
    columns.mapValues { 
      case i if i > idx => i - 1
      case i => i
    }
    activeCells.mapValues { _.remove(idx, 1) }
    assert(columns.map { _._2 }.toSet == (0 until columns.size).toSet)
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
              cell.recompute(row)
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

  def getFuture(col: ColumnRef, row: RowIndex): Future[Datum] =
  {
    assert(activeCells contains row, "Can only get active rows")
    val rowData = activeCells(row)
    val colIdx = columns(col)

    logger.trace(s"RowData @ $col[$row]: $rowData")

    rowData(colIdx) match 
    {
      case Some(cell) => cell.data
      case None => 
        sourceRowOffsets(row) match {
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
  {
    val pattern = new UpdatePattern(expression, nextUpdateIdx.getAndIncrement())

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

    def recompute(row: RowIndex)
    {
      data = Future {
        logger.trace(s"Computing ${pattern.expression}")
        pattern.expression.transform
        {
          case RValueExpression(cell@OffsetCell(col, 0)) => 
            logger.trace(s"Retrieving $cell")
            val result = Await.result(getFuture(col, row), Duration.Inf)
            logger.trace(s"Retrieved $cell")
            Literal(result)
          case RValueExpression(_) => 
            throw new VizierException("Formulas that reference cells outside of the same row are not currently supported.")
          case x:Unevaluable =>
            throw new VizierException(s"The formula '$x' [${x.getClass().getSimpleName()}] is not supported yet.")

        }.eval()
      }
    }

    override def toString: String =
      s"${data.value} <- '=${pattern.expression}'"
  }

}