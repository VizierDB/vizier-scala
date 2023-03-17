package info.vizierdb.spreadsheet

import scala.collection.mutable

/**
 * A component of the UpdateOverlay that is solely responsible for managing the 
 * forward and backward indices over the collection of [[UpdateRule]]s.
 */
class UpdateIndex
{
  type UpdateRuleId = Long

  /**
   * A mapping from cell ranges to the corresponding update rule
   * 
   * Cell ranges are given in the currently active reference frame.
   * The rules themselves are tagged with the reference frame they
   * were defined in.
   */
  val forwardIndex = mutable.Map[ColumnRef, RangeMap[UpdateRule]]()

  /**
   * An inverted index, mapping update rule identifiers to the ranges over
   * which the rule is actively defined.
   */
  val backwardIndex = mutable.Map[UpdateRuleId, (UpdateRule, ColumnRef, RangeSet)]()

  /**
   * A mapping from cell ranges to any downstream cells (i.e., cells that
   * depend on a cell in the range)
   * 
   * Cell ranges are given in the currently active reference frame.
   * The trigger targets are each tagged with the reference frame they were
   * defined in.
   */
  val triggerIndex = mutable.Map[ColumnRef, RangeMap[Seq[UpdateRule]]]()

  /**
   * The currently active reference frame
   */
  val frame = ReferenceFrame()

  /**
   * Change the shape by adding a column
   */
  def addColumn(column: ColumnRef)
  {
    forwardIndex.put(column, RangeMap.empty[UpdateRule])
    triggerIndex.put(column, RangeMap.empty[Seq[UpdateRule]])
  }

  /**
   * Change the shape by removing a column
   */
  def delColumn(column: ColumnRef)
  {
    forwardIndex.remove(column)
    triggerIndex.remove(column)
  }

  /**
   * Apply the provided update rule to a range of cells and update the
   * indexes accordingly
   */
  def putRange(column: ColumnRef, rows: RangeSet, rule: UpdateRule)
  {
    assert(!backwardIndex.contains(rule.id))

    // Remove all of the existing triggers in the target range
    removeRange(column, rows)

    // Insert the new update rule
    forwardIndex(column).insert(rows, rule)
    backwardIndex.put(rule.id, (
      rule,
      column,
      rows
    ))

    // And register any relevant triggers
    registerTriggers(rule, rows)
  }

  /**
   * Remove all updates from the specified range and update the indexes
   * accordingly
   */
  def removeRange(column: ColumnRef, rows: RangeSet)
  {
    // Slice removes the specified range(s) from the RangeMap
    // This updates the forward index
    val deletedRangesByRuleId = 
      forwardIndex(column).slice(rows)
    // The return value of slice includes all of the updates that reside
    // in the deleted range.  Cluster them by the rule id, and then
    // collapse them to a RangeSet.
                          .groupBy { _._3.id }
                          .mapValues { deleted =>
                            deleted.map { range => RangeSet(range._1, range._2) }
                                   .foldLeft(RangeSet.empty) { _ ++ _ } 
                          }
    // The result is a map of ruleIds to the ranges for the rule that were
    // just deleted.  For each of these...

    for( (ruleId, deletedRange) <- deletedRangesByRuleId )
    {
      // So here... 
      val (
        rule,          // ... is one of the rules in the removed range
        targetColumn,  // ... should be == column
        targetRange    // ... is the full range over which `rule` is
                       //     defined in `forwardIndex`
      ) = backwardIndex(ruleId)

      // ... and this is the new range over which `rule` will be defined
      //     after the range is removed.
      val updatedRange = targetRange -- deletedRange

      // This next bit is delicate: The same rule can define a trigger for
      // the same cell, albeit at different offsets.  Figuring out exactly
      // which triggers are invalidated is going to be a royal mess, so 
      // instead, we're going to just take a simpler approach:
      // 1. Deregister every existing trigger for the rule before
      // 2. Re-register every existing trigger for the rule after (if any exist)

      // Delete the update from all of its triggering ranges
      deregisterTriggers(rule, targetRange)

      if(updatedRange.isEmpty)
      {
        // all instances of the update rule have been deleted.  Clean it up
        backwardIndex.remove(ruleId)
      } else {
        // Insert the update into all of the ranges that still trigger it
        registerTriggers(rule, updatedRange)
        // and update the target range in the backward index
        backwardIndex.put(ruleId, (
          rule,
          targetColumn,
          updatedRange
        ))
      }
    }
  }

  /**
   * Deregister triggers for the specified rule.
   * @param  rule        The update rule to deregister for
   * @param  targetRange MUST be equal to backwardIndex(rule.id)._3
   */
  private def deregisterTriggers(rule: UpdateRule, targetRange: RangeSet): Unit =
  {
    for( (upstreamColumn, upstreamRows) <- rule.triggeringRanges(targetRange) )
    {
      triggerIndex(upstreamColumn).update(upstreamRows) {
        case (_, _, Some(triggeredRules)) =>
          // Delete the rule's trigger
          triggeredRules.filterNot { _.id == rule.id } match {
            case Seq() => None
            case x => Some(x)
          }
        case (_, _, None) => None
          // Odd... this shouldn't happen.  The rule wasn't registered
      }
    }
  }

  /**
   * Register triggers for the specified rule.
   * @param  rule        The update rule to deregister for
   * @param  targetRange MUST be equal to backwardIndex(rule.id)._3
   */
  private def registerTriggers(rule: UpdateRule, targetRange: RangeSet)
  {
    for( (upstreamColumn, upstreamRows) <- rule.triggeringRanges(targetRange) )
    {
      triggerIndex(upstreamColumn).update(upstreamRows) {
        case (_, _, Some(triggeredRules)) =>
          Some(rule +: triggeredRules)
        case (_, _, None) => 
          Some(Seq(rule))
      }
    }        
  }

  /**
   * Obtain the update rule at a specific position (if one exists)
   */
  def get(column: ColumnRef, row: Long): Option[UpdateRule] =
    forwardIndex(column)(row)

  /**
   * Compute all downstream dependencies of the provided range
   */
  def downstream(column: ColumnRef, rows: RangeSet)
  {
    val queue = mutable.Queue( (column, rows) )
    while(!queue.isEmpty)
    {

    }
  }

}