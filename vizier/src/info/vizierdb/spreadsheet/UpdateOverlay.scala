package info.vizierdb.spreadsheet

import scala.collection.mutable
import scala.concurrent.Future
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Column
import scala.collection.mutable.Queue
import scala.concurrent.Promise
import info.vizierdb.VizierException
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.typesafe.scalalogging.LazyLogging

/**
 * This class acts as a sort of 
 */
class UpdateOverlay(
  sourceValue: (ColumnRef, Long) => Any,
  cellModified: (ColumnRef, Long) => Unit = {(_,_) => ()}
)(implicit ec: ExecutionContext)
  extends LazyLogging
{
  type UpdateID = Long
  type RowIndex = Long

  var subscriptions = RangeSet()
  val updates = mutable.Map[UpdateID, UpdateRule]()
  var nextUpdate = 0l
  var nextInsert = 0l

  val dag = mutable.Map[ColumnRef,RangeMap[UpdateRule]]()
  val data = mutable.Map[ColumnRef, mutable.Map[RowIndex, Future[Any]]]()
  val triggers = mutable.Map[ColumnRef, mutable.Map[RowIndex, TriggerSet]]()
  var frame = ReferenceFrame()

  def activeTriggers(column: ColumnRef) = 
    RangeSet.ofIndices(triggers(column).keys.toSeq)

  def addColumn(name: ColumnRef) =
  {
    data.put(name, mutable.Map.empty)
    dag.put(name, new RangeMap[UpdateRule]())
    triggers.put(name, mutable.Map.empty)
  }

  def deleteColumn(name: ColumnRef) =
  {
    data.remove(name)
    dag.remove(name)
    triggers.remove(name)
  }

  def cellNeeded(cell: SingleCell): Boolean =
    cellNeeded(cell.column, cell.row)
  def cellNeeded(column: ColumnRef, row: RowIndex): Boolean =
    (subscriptions(row)) || (!triggers(column)(row).isEmpty)

  def requiredSourceRows: RangeSet =
  {
    triggers.foldLeft(subscriptions) { 
      case (accum, (column, triggers)) =>
        val lvalues = dag(column)
        RangeSet.ofIndices(
          triggers.keys
              // we only care about triggers going back to the base data
                  .filter { lvalues(_).isEmpty }
              // and we want these relative to the source frame
                  .map { row => frame.backward(RowByIndex(row)) }
              // ignore inserted rows
                  .collect { case RowByIndex(row) => row }
                  .toSeq
        ) ++ accum
    }
  }

  def update(target: LValue, col: Column): Unit =
    update(target, col.expr)

  def update(target: LValue, expression: Expression): Unit =
  {
    val rule = UpdateRule(expression, frame, nextUpdate);
    nextUpdate += 1
    updates.put(rule.id, rule)
    logger.trace(s"Inserting $rule @ $target")
    for( (from, to) <- target.toRangeSet )
    {
      for( (oldFrom, oldTo, oldRule) <- dag(target.column)(from, to) )
      {
        for( row <- oldFrom to oldTo )
        {
          if(!cellNeeded(target.column, row)){
            delTriggerFor(target.column, row, rule)
          }
        }
      }
      dag(target.column).insert(from, to, rule)
    }
    // logger.trace(dag(target.column).toString())

    // Since we're inserting a range of updates, figure out which of these
    // could potentially require re-execution
    val requiredTriggers = 
      (activeTriggers(target.column) ++ subscriptions) intersect target.toRangeSet

    logger.debug(s"Update requires triggers on rows $requiredTriggers")

    for(row <- requiredTriggers.indices)
    {
      addTriggerFor(target.column, row, rule)
    }

    triggerReexecution(
      requiredTriggers.indices.map { SingleCell(target.column, _) }.toSeq
    )
  }

  /**
   * Clean up triggers registered for rule being used to compute the specified cell
   * @param  column   The column of the cell to clean up triggers for
   * @param  row      The row of the cell to clean up triggers for
   * @param  rule     the rule used to compute the cell's value
   */
  def delTriggerFor(column: ColumnRef, row: RowIndex, rule: UpdateRule): Unit =
  {
    for(cell <- rule.triggeringCells(row, frame)){
      triggers(cell.column)(cell.row).remove(SingleCell(column, row), frame)
      if(!cellNeeded(cell)){
        delTriggerFor(cell.column, cell.row, dag(cell.column)(cell.row).get)
      }
    }
  }

  def addTriggerFor(column: ColumnRef, row: RowIndex, rule: UpdateRule): Unit =
  {
    // triggers get added under two circumstances:
    // 1. We have a subscription for the specified row
    // 2. One of the subscriptions depends on the specified row 
    //    (i.e., there is an existing trigger)

    if(cellNeeded(column, row)){
      for(cell <- rule.triggeringCells(row, frame)){
        if(triggers(cell.column) contains cell.row){
          triggers(cell.column)(cell.row).add(SingleCell(column, row), frame)
        } else {
          triggers(cell.column)(cell.row) = new TriggerSet(SingleCell(column, row), frame)

          // If this is the first reference to this cell, we may need to
          // recursively add it to the list of active dependencies
          dag(cell.column)(cell.row) match {
            case None => ()
            case Some(rule) => 
              addTriggerFor(cell.column, cell.row, rule)
          }
        }
      }
    }

  }

  def triggerReexecution(targets: Seq[SingleCell]): Unit = 
  {
    logger.debug(s"Starting re-execution pass with targets: $targets")

    def collectUpstream(accum: Set[SingleCell], cell: SingleCell): Set[SingleCell] = 
      // simple memoization.  Don't recur to the cell if we've already visited it
      if(accum contains cell){ accum }
      else {
        if((triggers(cell.column) contains cell.row) && 
            !triggers(cell.column)(cell.row).isEmpty)
        {
          triggers(cell.column)(cell.row)
            .iterator(frame)
            .foldLeft(accum + cell) { collectUpstream(_, _) }
        } else {
          accum + cell
        }
      }

    def collectDownstream(accum: Set[SingleCell], cell: SingleCell):Set[SingleCell] = 
      if(accum contains cell){ accum }
      else {
        dag(cell.column)(cell.row) match {
          case None => accum
          case Some(rule) => 
            rule.triggeringCells(cell.row, frame)
                .foldLeft(accum + cell){ collectDownstream(_, _) }
        }
      }

    val cellPromises =
      targets.foldLeft(Set[SingleCell]()) { (deps, cell) =>
          collectDownstream(
            collectUpstream(deps, cell),
            cell
          )
        }.toSeq
         .map { cell => cell -> Promise[Any]() }
         .toMap

    logger.debug(s"Final invalidated cells = ${cellPromises.keys.mkString(", ")}")

    for( (cell, promise) <- cellPromises )
    {
      data(cell.column)(cell.row) = promise.future
      cellModified(cell.column, cell.row)
    }

    Future {
      logger.debug(s"Starting evaluation pass for ${cellPromises.keys.mkString(", ")}")

      def compute(cell: SingleCell, promise: Promise[Any], blockedCells: Set[SingleCell] = Set.empty): Unit =
      {
        logger.trace(s"Compute $cell (completed = ${promise.isCompleted})")
        if(promise.isCompleted) { return }
        val rule = dag(cell.column)(cell.row).get
        try {
          for(dependency <- rule.triggeringCells(cell.row, frame)){
            assert(!blockedCells(dependency), "Recursive dependency")

            cellPromises.get(dependency) match {
              case None => () // dependency didn't need recomputation
              case Some(depPromise) => 
                // compute dependency first
                if(!promise.isCompleted){ compute(dependency, depPromise, blockedCells + cell) }
            }
          }
          promise.success(eval(cell.row, rule))
          cellModified(cell.column, cell.row)
        } catch {
          case e: Throwable => cellPromises(cell).failure(e)
        }
      }

      cellPromises.foreach { case (cell, promise) => compute(cell, promise) }
    }.onComplete { 
      case Success(_) => println("Finished processing cells")
      case Failure(err) => println(s"Error: $err")
    }

  }

  def getFuture(cell: SingleCell, targetFrame: ReferenceFrame = frame): Future[Any] =
  {
    assert(subscriptions(cell.row), "Reading from unsubscribed cell")
    logger.trace(s"Read from $cell")
    assert(data contains cell.column, "The cell ${cell}'s column was deleted")
    val rowInCurrentFrame = 
      targetFrame.relativeTo(frame)
                 .forward(RowByIndex(cell.row))
                 .getOrElse {
                    assert(false, "The cell ${cell}'s row was deleted")
                 }.asInstanceOf[RowByIndex].idx

    // logger.trace(dag(cell.column).toString())
    // logger.trace(s"Available: [${cell.column}:${data(cell.column).keys.mkString(",")}]")

    data(cell.column).get(rowInCurrentFrame) match {
      case None => // the cell comes from the source
        frame.backward(RowByIndex(rowInCurrentFrame)) match {
          case _:InsertedRow => 
            return null
          case RowByIndex(rowInOriginalFrame) => 
            return Future.successful(sourceValue(cell.column, rowInOriginalFrame))
        }
      case Some(future) => 
        return future
    }
  }

  def get(cell: SingleCell, targetFrame: ReferenceFrame = frame): Any =
  {
    getFuture(cell, targetFrame).value match {
              case None => assert(false, s"Trying to read pending cell $cell")
              case Some(Success(result)) => result
              case Some(Failure(err)) => 
                logger.error(err.toString())
                throw new Exception(s"Error while computing $cell", err)
           }
  }

  def getExpression(cell: SingleCell): Option[Expression] =
  {
    dag(cell.column)(cell.row)
      .map { rule =>
        if(rule.isLocal || (rule.frame eq frame)){ rule.expression }
        else {
          val frameOffset = rule.frame.relativeTo(frame)
          rule.expression.transform {
            case RValueExpression(OffsetCell(col, rowOffset)) if rowOffset != 0 =>
              val originalRow = frameOffset.backward(RowByIndex(cell.row))
                                           .asInstanceOf[RowByIndex].idx
              frameOffset.forward(originalRow + rowOffset) match {
                case None => 
                  InvalidRValue(s"${SingleCell(col, originalRow)} was deleted")
                case Some(newOffsetRow) => 
                  RValueExpression(OffsetCell(col, (newOffsetRow - cell.row).toInt))
              }

          }
        }
      }
  }

  def await(cell: SingleCell, targetFrame: ReferenceFrame = frame, duration: Duration = Duration.Inf): Any =
  {
    Await.result(getFuture(cell, targetFrame), duration)
  }

  def eval(targetRow: RowIndex, rule: UpdateRule): Any =
  {
    val offset = rule.frame.relativeTo(frame)
    val expr = 
      rule.expression.transform { 
        case RValueExpression(cell:SingleCell) => 
          logger.trace(s"Fetch $cell")
          lit(get(cell, rule.frame)).expr
        case RValueExpression(cell:OffsetCell) =>
          logger.trace(s"Fetch [${cell.column}:${cell.rowOffset}] @ $targetRow")
          lit(get(SingleCell(
            cell.column,
            offset.forward(
              offset.backward( RowByIndex(targetRow) )
                    .asInstanceOf[RowByIndex].idx 
                      + cell.rowOffset
            ).getOrElse {
              throw new AssertionError(s"Reading from deleted cell $cell")
            }
          ), rule.frame)).expr
      }
    logger.trace(s"Eval {$expr} @ $targetRow")
    return expr.eval(null)
  }

  def unsubscribe(rows: RangeSet): Unit =
  {
    logger.debug(s"Drpping subscriptions for $rows")
    subscriptions = subscriptions -- rows
    for( (column, lvalues) <- dag )
    {
      for( (searchFrom, searchTo) <- rows )
      {
        for( (from, to, rule) <- lvalues(searchFrom, searchTo) )
        {
          for( row <- from.to(to) )
          {
            if( !cellNeeded(column, row) )
            {
              delTriggerFor(column, row, rule)
              data(column).remove(row)
            }
          }
        }
      }
    }
  }

  def subscribe(rows: RangeSet, forceCells: Set[SingleCell] = Set.empty): Unit =
  {
    logger.debug(s"Adding subscriptions for $rows")
    subscriptions = subscriptions ++ rows
    val needsExecution = mutable.ArrayBuffer(forceCells.toSeq:_*)
    for( (column, lvalues) <- dag )
    {
      for( (searchFrom, searchTo) <- rows )
      {
        for( (from, to, rule) <- lvalues(searchFrom, searchTo) )
        {
          logger.trace(s"Checking range: $from-$to")
          for( row <- from.to(to) )
          {
            logger.trace(s"Checking if $column:$row is needed")
            if( !(data(column) contains row) )
            {
              addTriggerFor(column, row, rule)
              needsExecution.append(SingleCell(column, row))
            }
          }
        }
      }
    }
    logger.debug(s"Triggering re-execution for $needsExecution")
    triggerReexecution(needsExecution)
  }

  def updateSubscription(newSubscriptions: RangeSet, forceCells: Set[SingleCell] = Set.empty): Unit =
  {
    val noLongerInterestingRows = subscriptions -- newSubscriptions
    val newlyInterestingRows = newSubscriptions -- subscriptions

    unsubscribe(noLongerInterestingRows)
    subscribe(newlyInterestingRows, forceCells = forceCells)
  }

  def deleteRows(position: RowIndex, count: Int): Unit =
    migrateFrame(DeleteRows(position, count))

  def insertRows(position: RowIndex, count: Int): Unit =
  {
    val id = nextInsert
    nextInsert += 1
    migrateFrame(InsertRows(position, count, id))
  }

  def moveRows(from: RowIndex, to: RowIndex, count: Int): Unit =
  {
    assert(to < from || to >= from + count)
    migrateFrame(MoveRows(from, to, count))
  }

  /**
   * Utility function to assist in transitioning [[ReferenceFrame]]s
   * @param  op
   * 
   * This function will:
   * 1. Remap affected data values to their positions in the new frame
   * 2. Remap affected triggers to their positions Delete the new frame
   * 3. Register the new frame of reference
   * 4. Update the subscription
   * 
   * This function will NOT;
   * 1. Update the dag
   * 2. Clean up triggers for cells not mapped to a new position
   * 3. Trigger re-execution of cells depending on deleted cells and/or 
   *    required for the updated subscription.
   */
  def migrateFrame(op: RowTransformation)
  {
    val subscriptionSnapshot: RangeSet = subscriptions

    // Step 1: DeleteRows only
    // Once we delete the relevant lvalues, we won't bea able to 
    // unsubscribe from them anymore.  Do that now
    op match {
      case DeleteRows(position, count) =>
        unsubscribe(RangeSet(position, position+count-1))
      case _:InsertRows | _:MoveRows => ()
    }

    // Step 2
    // Remap positions in the dag
    for( (_, lvalues) <- dag )
    {
      op match {
        case DeleteRows(position, count) =>
          lvalues.collapse(position, count)
        case InsertRows(position, count, _) =>
          // If we're inserting a sequence of rows at this point, the
          // question arises of whether we want to fill in fields of the
          // inserted rows.  We have two strategies here:
          //   inject: insert blank cells
          //   expand: whatever update rule appears at position gets
          //           replicated to the newly created cells.
          // Note: the 'expand' strategy ony works if the cell's rule makes
          //       no references to offset rows, otherwise the values for those 
          //       inputs are ambiguous.

          // Our heuristic looks at position and the immediately preceding
          // position:
          (lvalues(position-1), lvalues(position)) match {
            // If a single update spans both positions, and the rule can be
            // safely expanded, then do that
            case (Some(lrule), Some(hrule)) if lrule.id == hrule.id && lrule.isLocal =>
              lvalues.expand(position, count)

            // If different rules appear at both positions, if either
            // position lacks a rule, or if the rule makes offset 
            // references, then fall back to injecting
            case _ => 
              lvalues.inject(position, count)
          }
        case MoveRows(from, to, count) =>
          lvalues.move(from, to, count)
      }
    }

    // Step 3
    // Remap data values
    for( (col, cells) <- data)
    {
      val original = cells.toIndexedSeq
      cells.clear

      for( (row, rule) <- original ) 
      { 
        op.forward(row) match {
          case None => 
            // logger.trace(s"$op deleted $col:$row")
          case Some(newRow) => 
            // logger.trace(s"$op moved $col:$row to $col:$newRow")
            cells.put(newRow, rule)
        }
      }
    }

    // Step 4
    // Remap triggers
    val deletedTriggers = mutable.ArrayBuffer[TriggerSet]()
    for( (_, triggers) <- triggers )
    {
      val original = triggers.toIndexedSeq
      triggers.clear

      for( (row, targets) <- original ) 
      { 
        op.forward(row) match {
          case None => deletedTriggers.append(targets)
          case Some(newRow) => triggers.put(newRow, targets)
        }
      }
    }

    ////// Step 4 //////
    frame = frame + op

    ////// Step 5 //////
    val desiredSubscription = subscriptionSnapshot
    subscriptions = 
      op match {
        case DeleteRows(position, count) =>
          subscriptionSnapshot.collapse(position, count)
        case InsertRows(position, count, _) =>
          subscriptionSnapshot.inject(position, count)
        case MoveRows(from, to, count) =>
          subscriptionSnapshot.collapse(from, count)
                              .inject(if(from < to){to-count} else {to}, count) ++
          (subscriptionSnapshot intersect RangeSet(from, from+count-1))
            .offset(if(from < to){ to - from - count} else { to - from })
      }

    logger.trace(s"After $op: \nSubscriptions: $subscriptions\nBut want: $desiredSubscription")

    updateSubscription(
      desiredSubscription, 
      forceCells = deletedTriggers.flatMap { _.iterator(frame) }.toSet
    )
  }
}

class TriggerSet(var frame: ReferenceFrame)
{
  val triggers = mutable.Set[SingleCell]()

  def this(cell: SingleCell, frame: ReferenceFrame)
  {
    this(frame)
    add(cell, frame)
  }

  def add(cell: SingleCell, cellFrame: ReferenceFrame): Unit =
  {
    if(!(frame eq cellFrame)){ updateFrame(cellFrame) }
    triggers.add(cell)
  }

  def remove(cell: SingleCell, cellFrame: ReferenceFrame): Unit =
  {
    if(!(frame eq cellFrame)){ updateFrame(cellFrame) }
    triggers.remove(cell)
  }

  def iterator(cellFrame: ReferenceFrame): Iterator[SingleCell] =
  {
    if(!(frame eq cellFrame)){ updateFrame(cellFrame) }
    triggers.iterator
  }

  def isEmpty = triggers.isEmpty

  def updateFrame(newFrame: ReferenceFrame): Unit = 
  {
    val offset = newFrame.relativeTo(frame)
    val oldTriggers = triggers.toIndexedSeq
    triggers.clear
    for(trigger <- oldTriggers){
      offset.forward(RowByIndex(trigger.row)) match {
        case None => // row deleted.  Ignore!
        case Some(RowByIndex(idx)) => 
          triggers.add(SingleCell(trigger.column, idx))
        case Some(InsertedRow(_, _)) => //should never happen
      }
    }
  }
}
