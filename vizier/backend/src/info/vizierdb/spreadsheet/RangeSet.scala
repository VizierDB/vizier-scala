/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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

/**
 * A rough analog of Set[Long], but designed to group continuous values
 * of integers together into disjoint ranges expressed in terms of 
 * closed high/low bounds.
 * 
 * <b>All</b> bounds (both high and low) are <b>inclusive</b>
 * 
 * The key gimmick of range set is that it stores its ranges in
 * sorted order.  This allows us to perform most bulk operations 
 * (e.g., intersect, union) in O(ranges.size), and most point 
 * operations (e.g., lookup) in O(log(ranges.size))
 */
class RangeSet(ranges: Seq[(Long, Long)])
  extends Iterable[(Long, Long)]
{
  /**
   * Generate a new range set by adding values in the range [low, high].
   * @param range     Inclusive bounds for the range to add.
   * @return          The updated [[RangeSet]]
   * 
   * If the new range overlaps any existing ranges, the overlapping 
   * ranges will be coalesced together.
   * 
   * Preconditions:
   * - `ranges` is sorted on _._1
   * Postconditions: 
   * - `ranges` includes exactly one element that fully contains 
   *   `range`, and no elements that partially overlap with it.
   * - if there exists an element of `ranges` that contains 
   *   the value `range._1 - 1`, then it is the same element that 
   *   contains `range` (respectively for `range._2+1`)
   * 
   * This operation is O(ranges.size)
   */
  def add(range: (Long, Long)): RangeSet = 
  {
    // it's a bit dumb, but the following implements add as a single step of bubble sort
    new RangeSet(ranges.foldRight(range :: Nil) { 
      // inserted range >> the next element; bubble up
      case (head, tail) if head._1 > tail.head._2 + 1 => tail.head :: head :: tail.tail
      // inserted range << the next element; leave in-situ
      case (head, tail) if tail.head._1 > head._2 + 1 => head :: tail
      // otherwise, merge ranges
      case (head, tail) => (math.min(range._1, head._1), math.max(range._2, head._2)) :: tail
    })
  }

  /**
   * Generate a new range set that excludes all elements in a specified range
   * @param range     Inclusive bounds for the range to remove
   * @return          The updated [[RangeSet]]
   * 
   * Postconditions:
   * - `ranges` includes no elements that partially overlap with
   *   `range`.  
   * - Any elements of `ranges` fully contained in `range` are removed
   * 
   * This operation is O(ranges.size)
   */
  def remove(range: (Long, Long)): RangeSet =
  {
    new RangeSet(
      ranges.flatMap {
        case x if x._2 < range._1 => Seq(x)
        case x if x._1 > range._2 => Seq(x)
        case x if x._1 >= range._1 && x._2 <= range._2 => Seq.empty
        case x if x._1 < range._1 && x._2 <= range._2 => Seq( (x._1, range._1-1) )
        case x if x._1 >= range._1 && x._2 > range._2 => Seq( (range._2+1, x._2) )
        case x => Seq((x._1, range._1-1), (range._2+1, x._2))
      }
    )
  }

  /**
   * Retrieve the range containing the specified point, if one exists
   * @param point    The point to look up
   * @return         The inclusive upper/lower bounds of the range 
   *                 containing the point, or None if no such range
   *                 exists.
   * 
   * This operation is O(log(ranges.size))
   */
  def get(point: Long): Option[(Long, Long)] =
  {
    ranges.find { x => x._1 <= point && x._2 >= point }
  }

  /**
   * Test whether the specified point is contained in this set.
   * @param point    The point to test
   * @return         True iff the point is contained in this RangeSet
   * 
   * This operation's runtime is equivalent to `get`
   */
  def apply(point: Long): Boolean = get(point).isDefined

  /**
   * Generate a new range set by adjusting the 
   * @param amount    The amount to offset by.
   * @return          The updated [[RangeSet]]
   */
  def offset(amount: Long): RangeSet = 
  {
    new RangeSet(
      ranges.map { x => (x._1 + amount, x._2 + amount) }
    )
  }

  /**
   * Return the number of points defined at or below the specified point
   * @param point   The point to use as an *inclusive* upper bound
   * @return        The number of points in the range set up to this point
   * 
   * This operation's runtime is O(size) (can probably make it better)
   */
  def countTo(point: Long): Long =
  {
    ranges.map {
      case (low, high) if high <= point => high-low+1
      case (low, high) if low <= point  => point-low+1
      case _ => 0
    }.sum
  }

  /**
   * Generate two new range sets by splitting at the specified point
   * @param point     The point to split at
   * @return          Two range sets.  The first contains all ranges
   *                  up to and including `point`, The second contains
   *                  all ranges above and including `point+1`.
   * 
   * If point appears in an element of ranges, then the range in which
   * it appears will be bisected at point (point will go into the lower)
   * range.
   * 
   * This operation is O(ranges.size) for simplicity, but could probably
   * be rewritten to be O(log(ranges.size) + cost of Seq.splitAt) if
   * necessary.
   */
  def split(point: Long): (RangeSet, RangeSet) =
    (
      new RangeSet(
        ranges.flatMap { 
          case x if x._1 < point && x._2 < point => Some(x)
          case x if x._1 < point => Some( (x._1, point) )
          case x => None
        }
      ), new RangeSet(
        ranges.flatMap { 
          case x if x._1 > point+1 && x._2 > point+1 => Some(x)
          case x if x._2 > point+1 => Some( (point+1, x._2) )
          case x => None
        }
      )
    )

  /**
   * Compute the union of two RangeSets
   * @param other     The other range set to merge 
   *
   * The runtime of this function is O(N log(N))
   * for N = ranges.size + other.ranges.size, but we could rewrite
   * it to be O(N) by implementing MergeJoin if necessary.
   */
  def ++(other: RangeSet): RangeSet =
    // Note: RangeSet, rather than new RangeSet will handle sorting
    // and overlap merges for is.
    RangeSet( (iterator ++ other.iterator).toSeq )

  /**
   * Compute the intersection of two RangeSets
   * @param other      The other range set to merge
   * 
   * Preconditions:
   * - ranges is sorted
   * 
   * The runtime of this function is O(ranges.size + other.ranges.size)
   */
  def intersect(other: RangeSet): RangeSet =
  {
    val myElements = ranges.iterator.buffered
    val otherElements = other.iterator.buffered
    // Note: new RangeSet does not sort or merge overlap.
    // Define a MergeJoin as an inline iterator.
    new RangeSet(
      new Iterator[(Long, Long)](){

        /**
         * Advance the iterators until their heads include a range
         * that contains at least one overlapping value.
         * @return       False iff there are no more overlapping ranges
         */
        def skipToOverlap: Boolean =
        {
          while(myElements.hasNext && otherElements.hasNext)
          {
            if(myElements.head._1 <= otherElements.head._1) {
              if(myElements.head._2 >= otherElements.head._1) { return true }
              else { myElements.next }
            } else {
              if(otherElements.head._2 >= myElements.head._1) { return true }
              else { otherElements.next }
            }
          }
          return false
        }

        def hasNext: Boolean = skipToOverlap
        def next = 
        {
          // Start by advancing the iterators until they intersect
          skipToOverlap

          // The next returned range is computed as:
          // Lower bound = the maximal lower bounds of the iterator 
          //               heads.
          // Upper bound = the minimal upper bounds of the iterator
          //               heads.
          // 
          // Notably, we only advance the iterator with the least
          // upper bound.  We leave the greater upper bound range on 
          // its iterator in case there's another range that intersects 
          // with it.  
          //
          // For example:
          //   me    = [ (8, 43) ]
          //   other = [ (5, 9), (10, 20), (30,50) ]
          // 
          // We should return: 
          //  - (8, 9):   my lower bound is greater (5 < 8)
          //              other's upper bound is lesser (9 < 43)
          //              advance other (via next)
          //  - (10, 20): other's lower bound is greater (8 < 10)
          //              other's upper bound is lesser (20 < 43)
          //              advance other (via next)
          //  - (30, 43): other's lower bound is greater (8 < 30)
          //              my upper bound is lesser (43 < 50)
          //              advance me (via next)
          //   - my iterator is empty
          if(myElements.head._1 <= otherElements.head._1) {
            if(myElements.head._2 <= otherElements.head._2) {
              (otherElements.head._1, myElements.next._2)
            } else {
              otherElements.next
            }
          } else {
            if(otherElements.head._2 <= myElements.head._2) {
              (myElements.head._1, otherElements.next._2)
            } else {
              myElements.next
            }
          }
        }
      }.toSeq
    )
  }

  /**
   * Test if this RangeSet is a superset of the other one
   * @param other     The other RangeSet to test
   * @return          True iff every range in other is fully contained
   *                  within one of this RangeSet's ranges
   * 
   * This method is O(N) for N = ranges.size + other.ranges.size
   */
  def supset(other: RangeSet) =
    (other -- this).isEmpty

  /**
   * Subtract the ranges in the other RangeSet from this one.
   * @param other     The RangeSet to subtract from this one
   * @return          The updated RangeSet
   * 
   * This method is semantically identical to:
   * ```
   * other.foldLeft(this) { remove(_) }
   * ```
   * However, since remove is O(N), the above approach is O(N^2).
   * 
   * Preconditions:
   * - ranges (and other.ranges) is sorted
   * - ranges (and other.ranges) is disjoint
   * 
   * This method is O(N) for N = ranges.size + other.ranges.size
   */
  def --(other: RangeSet): RangeSet =
  {
    val otherElements = other.iterator.buffered
    new RangeSet(
      // Note: new RangeSet does not sort or merge overlap.
      // Define a MergeJoin as an inline iterator.
      new Iterator[(Long, Long)]() {
        var myElements = ranges.toList

        /**
         * Advance the otherElements iterator until it no longer
         * overlaps with myElements.head, slicing the iterator's 
         * head range off of myElements.head as we go along.
         */
        def bufferOne: Boolean = 
        {

          /* 
           * Given: me, other's heads
           * Case 0: nothing left to subtract
           * Case 1: nothing left to subtract from
           * Case 2:
           *      |---me---|               ... and then me.tail
           * -                 |--other--| ... and then other.tail
           * =    |---me---|               ... tail unchanged
           * Case 3:
           *              |---me---|       ... and then me.tail
           * -  |-other-|                  ... and then other.tail
           * =            |---me---|       ... tail unchanged
           * Case 4:
           *      |---me---|               ... and then me.tail
           * -  |---other----|             ... and then other.tail
           * =      empty                  ... tail unchanged
           * Case 5:
           *      |-----me-----|           ... and then me.tail
           * - |--other--|                 ... and then other.tail
           * =            |-me-|           ... tail unchanged
           * Case 6:
           *      |------me-----|          ... and then me.tail
           * -        |other|              ... and then other.tail
           * =    |me|       |me|          ... tail unchanged
           * Case 7:
           *      |------me-----|          ... and then me.tail
           * -        |---other---|        ... and then other.tail
           * =    |me|                     ... tail unchanged
           */



          // Case 0: If otherElements is empty, then we don't need to 
          // remove anything else from myElements.
          while(otherElements.hasNext){
            // Case 1: myElements is empty, nothing more to return
            if(myElements.isEmpty){ return false }

            // Case 2: There is no overlap between myElements.head and
            //         otherElements.head and the latter range is
            //         greater than the former. myElements.head is now
            //         safe to include in the returned RangeSet.
            if(otherElements.head._1 > myElements.head._2) { return true }

            // Cases 3-4: otherElements.head overlaps myElements.head
            //            at the lower bound.
            if(otherElements.head._1 <= myElements.head._1) {
              // Case 3: There is no overlap between myElements.head
              //         and otherElements.head, and the latter range
              //         is lesser than the former.  discard
              //         otherElements.head and try again.
              if(otherElements.head._2 < myElements.head._1){
                otherElements.next

              // Case 4: otherElements.head fully contains 
              //         myElements.head.  Remove the latter and try
              //         again with the next item in myElements.
              } else if(otherElements.head._2 >= myElements.head._2){
                myElements = myElements.tail

              // Case 5: otherElements.head only partly overlaps with
              //         myElements.head.  Truncate myElements.head's
              //         lower bound and try again with the next
              //         item in otherEements.
              } else {
                myElements = (otherElements.next._2+1, myElements.head._2) :: myElements.tail
              }

            // Case 6: (since cases 2 and 3 failed) myElements.head
            //         fully contains otherElements.head. Partition
            //         myElements.head by otherElements.head.
            } else if(otherElements.head._2 < myElements.head._2){
              myElements = (myElements.head._1, otherElements.head._1-1) ::
                           (otherElements.head._1, myElements.head._2) :: 
                           myElements.tail
              // Since we just added a myElements.head, we can't fail
              // case 1.
              // Since ranges is disjoint, we will definitely hit
              // case 2 next cycle, so shortcut and return true now.
              return true

            // Case 7: otherElements.head only partly overlaps with 
            //         myElements.head at the upper bound.  Truncate
            //         myElements.head accordingly.
            } else {
              myElements = (myElements.head._1, otherElements.head._1-1) ::
                           myElements.tail
              // Since we just added a myElements.head, we can't fail
              // case 1.
              // Since ranges is disjoint, we will definitely hit
              // case 2 next cycle, so shortcut and return true now.
              return true              
            }
          }
          return !myElements.isEmpty
        }

        def hasNext = bufferOne
        def next: (Long, Long) = { 
          // bufferOne will ensure that myElements.head (if it exists)
          // is free of anything that needs to be subtracted.
          bufferOne; 
          val ret = myElements.head
          myElements = myElements.tail
          return ret
        }
      }.toSeq
    )
  }

  /**
   * Collapse elements in the range [target, target+count)
   * 
   * This is a shortcut for the following steps:
   * 1. A, B = split(target-1)
   * 2. B = B.remove((target, target+count-1))
   * 3. B = B.offset(-count)
   * 4. A ++ B
   * 
   * For example:
   * [ (0, 10), (15, 20), (25, 30) ]
   * collapse(16, 2)
   * [ (0, 10), (15, 18), (23, 28) ]
   * ... remove elements 16, 17
   * ... slide elements >= 18 left by two spots.
   */
  def collapse(target: Long, count: Int): RangeSet = 
  {
    RangeSet(
      ranges.flatMap {
        case (from, to) if to < target => 
          Seq( (from, to) )
        case (from, to) if from < target && to < target + count => 
          Seq( (from, target-1) )
        case (from, to) if from < target => 
          Seq( (from, target-1), (target, to - count) )
        case (from, to) if to < target + count => 
          None
        case (from, to) if from < target + count => 
          Seq( (target, to - count) )
        case (from, to) => 
          Seq( (from - count, to - count) )
      }
    )
  }

  /**
   * Inject an empty spot into the range [target, target+count)
   * 
   * This is a shortcut for the following steps:
   * 1. A, B = split(target-1)
   * 2. B = B.offset(count)
   * 3. A ++ B
   */
  def inject(target: Long, count: Int): RangeSet = 
  {
    RangeSet(
      ranges.flatMap {
        case (from, to) if to < target => 
          Seq{ (from, to) }
        case (from, to) if from < target && to >= target => 
          Seq( (from, target-1), (target+count, to+count) )
        case (from, to) => 
          Seq( (from + count, to + count) )
      }
    )
  }

  override def toString(): String = 
    "RangeSet( " + toSeq.map { case (from, to) => s"[$from-$to]" }.mkString(", ") + " )"

  def iterator = ranges.iterator

  def indices = 
    ranges.iterator
          .flatMap { case (from, to) => from.to(to).iterator }

  def bounds: Option[(Long, Long)] =
    if(ranges.isEmpty){ None }
    else {
      Some( (ranges.head._1, ranges.last._2) )
    }

  override def isEmpty: Boolean = ranges.isEmpty
}

object RangeSet
{
  def apply(): RangeSet = new RangeSet(Seq.empty)
  def apply(x: Long) = new RangeSet(Seq( (x, x) ))
  def apply(low: Long, high: Long) = new RangeSet(Seq( (low, high) ))
  def apply(ranges: Seq[(Long, Long)]): RangeSet =
    new RangeSet(
      ranges.sortBy { _._2 }
            .foldRight(Nil:List[(Long, Long)]) { 
              case (elem, Nil) => elem :: Nil
              case (elem@(from, to), ret) => 
                if(ret.head._1-1 <= to){
                  (math.min(ret.head._1, from), ret.head._2) :: ret.tail
                } else {
                  elem :: ret
                }
            }
    )
  def ofIndices(indices: Seq[Long]) =
    new RangeSet(
      indices.sorted
             .foldRight(Nil:List[(Long, Long)]) { 
                case (a, Nil) => (a, a) :: Nil
                case (a, (b, c) :: rest) if a+1 == b => (a, c) :: rest
                case (a, rest) => (a, a) :: rest
              }
    )
}