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

/**
 * This class acts as a sort of 
 */
class Executor(sourceValue: (ColumnRef, Long) => Any)
              (implicit ec: ExecutionContext)
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

  def update(target: LValue, col: Column): Unit =
    update(target, col.expr)

  def update(target: LValue, expression: Expression): Unit =
  {
    val rule = UpdateRule(expression, frame, nextUpdate);
    nextUpdate += 1
    updates.put(rule.id, rule)
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

    // Since we're inserting a range of updates, figure out which of these
    // could potentially require a deletion
    val requiredTriggers = 
      (activeTriggers(target.column) ++ subscriptions) intersect target.toRangeSet

    for(row <- requiredTriggers.indices)
    {
      addTriggerFor(target.column, row, rule)
    }

    triggerReexecution(target.column -> requiredTriggers)
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

  def triggerReexecution(targets: (ColumnRef, RangeSet)*): Unit = 
  {
    val targetCells = 
      targets.flatMap { case (col, rows) => rows.indices.map { SingleCell(col, _) } }
    val workQueue = Queue[SingleCell]()

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
          workQueue.enqueue(cell)
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


    Future {
      val cellPromises =
        targetCells.foldLeft(Set[SingleCell]()) { (deps, cell) =>
            collectDownstream(
              collectUpstream(deps, cell),
              cell
            )
          }.toSeq
           .map { cell => cell -> Promise[Any]() }
           .toMap

      for( (cell, promise) <- cellPromises )
      {
        data(cell.column)(cell.row) = promise.future
      }


      def compute(cell: SingleCell, blockedCells: Set[SingleCell] = Set.empty): Unit =
      {
        val rule = dag(cell.column)(cell.row).get
        try {
          for(dependency <- rule.triggeringCells(cell.row, frame)){
            assert(!blockedCells(dependency), "Recursive dependency")

            cellPromises.get(dependency) match {
              case None => () // dependency didn't need recomputation
              case Some(promise) => 
                // compute dependency first
                if(!promise.isCompleted){ compute(cell, blockedCells + cell) }
            }
          }
        } catch {
          case e: Throwable => cellPromises(cell).failure(e)
        }
      }

      cellPromises.keys.foreach { compute(_) }
    }.onComplete { 
      case Success(_) => println("Finished processing cells")
      case Failure(err) => println(s"Error: $err")
    }

  }

  def get(cell: SingleCell, targetFrame: ReferenceFrame): Any =
  {
    assert(data contains cell.column, "The cell ${cell}'s column was deleted")
    val rowInCurrentFrame = 
      targetFrame.relativeTo(frame)
                 .forward(SourceRowByIndex(cell.row))
                 .getOrElse {
                    assert(false, "The cell ${cell}'s row was deleted")
                 }.asInstanceOf[SourceRowByIndex].idx

    data(cell.column).get(rowInCurrentFrame) match {
      case None => // the cell comes from the source
        frame.backward(SourceRowByIndex(rowInCurrentFrame)) match {
          case _:InsertedRow => 
            return null
          case SourceRowByIndex(rowInOriginalFrame) => 
            return sourceValue(cell.column, rowInOriginalFrame)
        }
      case Some(future) => 
        assert(future.isCompleted, s"Trying to read pending cell $cell")
        return future.value
    }
 }

  def compute(targetRow: RowIndex, rule: UpdateRule): Any =
  {
    rule.expression.transform { 
      case UnresolvedRValueExpression(cell:SingleCell) => 
        lit(get(cell, rule.frame)).expr
      case UnresolvedRValueExpression(cell:OffsetCell) =>
        lit(get(SingleCell(
          cell.column,
          rule.frame.relativeTo(frame)
                    .backward( SourceRowByIndex(targetRow) )
                    .asInstanceOf[SourceRowByIndex].idx
        ), rule.frame)).expr
    }.eval(null)
  }

  def unsubscribe(rows: RangeSet): Unit =
  {
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
    subscriptions = subscriptions ++ rows
    val needsExecution = mutable.ArrayBuffer(forceCells.toSeq:_*)
    for( (column, lvalues) <- dag )
    {
      for( (searchFrom, searchTo) <- rows )
      {
        for( (from, to, rule) <- lvalues(searchFrom, searchTo) )
        {
          for( row <- from.to(to) )
          {
            if( !(data(column) contains row) )
            {
              addTriggerFor(column, row, rule)
              data(column).remove(row)
            }
          }
        }
      }
    }
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
          lvalues.expand(position, count)
        case MoveRows(from, to, count) =>
          lvalues.move(from, to, count)
      }
    }

    // Step 3
    // Remap data values
    for( (_, cells) <- data)
    {
      val original = cells.toIndexedSeq
      cells.clear

      for( (row, rule) <- original ) 
      { 
        op.forward(row) match {
          case None => // deleted
          case Some(newRow) => cells.put(newRow, rule)
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
      offset.forward(SourceRowByIndex(trigger.row)) match {
        case None => // row deleted.  Ignore!
        case Some(SourceRowByIndex(idx)) => 
          triggers.add(SingleCell(trigger.column, idx))
        case Some(InsertedRow(_, _)) => //should never happen
      }
    }
  }
}
