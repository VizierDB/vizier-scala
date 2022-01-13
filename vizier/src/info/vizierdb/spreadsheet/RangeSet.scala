package info.vizierdb.spreadsheet

import scala.collection.mutable

class RangeSet(ranges: Seq[(Long, Long)])
  extends Iterable[(Long, Long)]
{
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

  def get(point: Long): Option[(Long, Long)] =
  {
    ranges.find { x => x._1 <= point && x._2 >= point }
  }

  def offset(amount: Long): RangeSet = 
  {
    new RangeSet(
      ranges.map { x => (x._1 + amount, x._2 + amount) }
    )
  }

  def split(at: Long): (RangeSet, RangeSet) =
    (
      new RangeSet(
        ranges.flatMap { 
          case x if x._1 < at && x._2 < at => Some(x)
          case x if x._1 < at => Some( (x._1, at) )
          case x => None
        }
      ), new RangeSet(
        ranges.flatMap { 
          case x if x._1 > at+1 && x._2 > at+1 => Some(x)
          case x if x._2 > at+1 => Some( (at+1, x._2) )
          case x => None
        }
      )
    )

  def ++(other: RangeSet): RangeSet =
    RangeSet( (iterator ++ other.iterator).toSeq )

  def intersect(other: RangeSet): RangeSet =
  {
    val myElements = ranges.iterator.buffered
    val otherElements = other.iterator.buffered
    new RangeSet(
      new Iterator[(Long, Long)](){

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
          skipToOverlap
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

  override def toString(): String = 
    "RangeSet( " + toSeq.map { case (from, to) => s"[$from, $to]" }.mkString(", ") + " )"

  def iterator = ranges.iterator
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
}