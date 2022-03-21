package info.vizierdb.spreadsheet

import scala.collection.mutable
import breeze.numerics.exp
import play.api.libs.json._
import play.api.libs.functional.syntax._
/**
 * A class for mapping update ranges to the specified UpdateCell expressions.
 */
class RangeMap[T]()
{

  /**
   * All ranges stored in the map 
   * assumptions: 
   * 1. All ranges are non-overlapping
   * 2. None of the stored [[UpdateCell]]s is defined over a [[FullColumnRange]].
   */
  val data = mutable.TreeMap[Long, (Long, T)]()

  /**
   * Find all ranges intersecting with the specified range
   * @param     from     The lower bound to search (inclusive)
   * @param     to       The upper bound to search (inclusive)
   * @param     clamp    If true, returned bounds will be clamped to the range 
   *                     (from, to); if false, the bounds in the map itself will
   *                     be returned.
   * @return             A sequence of range (from/to) pairs along with the
   *                     corresponding elements.
   */
  def apply(from: Long, to: Long, clamp: Boolean = true): Seq[(Long, Long, T)] = 
  {
    // println(s"Apply: $from -> $to\n$this")
    // With the comparator defined above, [[TreeMap]]'s to method gives us a 
    // view of the map ranging from the lowest range stored, up through the 
    // first range with alower-bound less than or equal to to.
    //
    // In principle, since the scala docs call this a 'view', this should be
    // a constant time operation that adds at worst a log-time overhead to all
    // subsequent function cals
    data.to(to)       

    // Iterate over the elements of the list in reverse order.  Unfortunately,
    // scala doesn't give us a 'reverseIterator' method, so we make do with
    // foldRight.   (cleaner would be something like 
    // .reverseIterator.takeUntil { ... }.map { ... }.toSeq)
        .foldRight(Nil:List[(Long, Long, T)]) { 
          // Sequences are non-overlapping, so if we hit an element with an upper
          // bound that is lower than the lower bound of our search, we can 
          // abort early.
          case ( (cmpFrom, (cmpTo, _)), ret ) if cmpTo < from => 
            // println(s"  ret @ $cmpFrom"); 
            return ret

          case ( (cmpFrom, (cmpTo, element)), ret ) if cmpFrom > to => 
            // println(s"  discard @ $cmpFrom")
            /* skip */ ret

          // The final case is the one where the UpdateCell we're now looking at
          // covers ALL of the range we need to append next.  In that case, 
          // append it and move on.
          case ( (cmpFrom, (cmpTo, element)), ret ) if clamp => 
            // println(s"  accumulate @ $cmpFrom")
            (math.max(cmpFrom, from), math.min(cmpTo, to), element) :: ret

          case ( (cmpFrom, (cmpTo, element)), ret ) => 
            // println(s"  accumulate @ $cmpFrom")
            (cmpFrom, cmpTo, element) :: ret
        }
  }

  def apply(at: Long): Option[T] =
    apply(at, at).headOption.map { _._3 }

  /**
   * Insert an element into the structure.
   * @param   element      The element to insert
   * @param   insertFrom   The start of the range to insert at
   * @param   insertTo     The end of the range to insert at
   */
  def insert(insertFrom: Long, insertTo: Long, element: T): Unit =
  {
    // Clean up any existing updates that this update may end up overwriting
    slice(insertFrom, insertTo)
    
    // Finally insert the updated values
    data.put( insertFrom, (insertTo, element) )
    onInsert(insertFrom, insertTo, element)
  }

  def clear(): Unit =
  {
    for( (from, (to, element)) <- data )
    {
      onRemove(from, to, element)
    }
    data.clear
  }

  /**
   * Slice a range of elements out of the map
   * @param  sliceFrom    The lower bound (inclusive) to slice out of the map
   * @param  sliceTo      The upper bound (inclusive) to slice out of the map
   * @return              A list of all (subsets of) ranges that (partially) overlap with [from, to]
   * 
   * Postcondition: 
   *  - There are no ranges in `data` from sliceFrom to sliceTo (inclusive)
   *  - Any range that overlapped sliceFrom is included with its upper bound cut to sliceFrom-1
   *  - Any range that overlapped sliceTo is included with its lower bound cut to sliceTo+1
   *  - Any range that overlapps both is 'bisected' into two separate ranges.
   * 
   * Any range that overlaps [sliceFrom, sliceTo] is returned.  If the overlap is only partial, 
   * then only the overlapping region is returned
   */
  def slice(sliceFrom: Long, sliceTo: Long): Seq[(Long, Long, T)] =
  {
    apply(sliceFrom, sliceTo, clamp = false) match {
      // no overlap = no cleanup
      case Seq() => 
        // println(s"No Slice ($sliceFrom -> $sliceTo)")
        return Seq.empty

      // Case 1: The update bisects a single update
      case Seq((cmpFrom, cmpTo, bisectedElement)) if (cmpFrom < sliceFrom) && (cmpTo > sliceTo) =>
      {
        // println("Bisect!")
        val copyA = cloneElement(bisectedElement)
        data.put(cmpFrom, (sliceFrom-1, bisectedElement))
        data.put(sliceTo+1, (cmpTo, copyA))

        onRemove(cmpFrom, cmpTo, bisectedElement)
        onInsert(cmpFrom, sliceFrom-1, bisectedElement)
        onInsert(sliceTo+1, cmpTo, bisectedElement)

        if(sliceTo - sliceFrom + 1 > 0) { 
          val copyB = cloneElement(bisectedElement)
          return Seq((sliceTo, sliceFrom, copyB))
        } else {
          return Seq.empty
        }
      }

      // Case 2: Everything else
      case sliced =>
      {
        // println(s"Sliced($sliceFrom -> $sliceTo) : $sliced")
        var entriesToDelete = sliced

        var headOption: Option[(Long, Long, T)] = None
        var tailOption: Option[(Long, Long, T)] = None

        // Case 2.1: The first entry is split in half
        if(entriesToDelete.head._1 < sliceFrom){
          val (headFrom, headTo, headElement) = entriesToDelete.head

          data.remove(headFrom)
          data.put(headFrom, (sliceFrom-1, headElement))

          onRemove(headFrom, headTo, headElement)
          onInsert(headFrom, sliceFrom-1, headElement)

          headOption = Some( (sliceFrom, headTo, cloneElement(headElement) ))
          entriesToDelete = entriesToDelete.tail
        }

        // Case 2.2: The last entry is split in half (Case 2.1 and 2.2 must be 
        // different updates, or else we'd be in Case 1)
        if(!entriesToDelete.isEmpty && entriesToDelete.last._2 > sliceTo){
          val (tailFrom, tailTo, tailElement) = entriesToDelete.last

          data.remove(tailFrom)
          data.put(sliceTo+1, (tailTo, tailElement))

          onRemove(tailFrom, tailTo, tailElement)
          onInsert(sliceTo+1, tailTo, tailElement)

          tailOption = Some( (tailFrom, sliceTo, cloneElement(tailElement)) )
          entriesToDelete = entriesToDelete.dropRight(1)
        }

        // Everything else can be fully deleted
        for( (delFrom, delTo, delElement) <- entriesToDelete){
          data.remove(delFrom)
          onRemove(delFrom, delTo, delElement)
        }
        return headOption.toSeq ++ entriesToDelete ++ tailOption
      }
    }
  }

  /**
   * Analogous to slice, but shift records following the silce to fill the gap.
   */
  def collapse(idx: Long, count: Long): Seq[(Long, Long, T)] =
  {
    // Delete entries in the slice
    val ret = slice(idx, idx+count-1)

    // Shift entries after the slice back
    for( (shiftFrom, (shiftTo, element)) <- data.from(idx).toIndexedSeq){
      data.remove(shiftFrom)
      data.put(shiftFrom - count, (shiftTo - count, element))
      onRemove(shiftFrom, shiftTo, element)
      onInsert(shiftFrom - count, shiftTo - count, element)
    }

    return ret
  }

  def inject(idx: Long, count: Long) =
  {    
    if(count > 0){ 
      bisect(idx) 
      // need to go in descending order to avoid accidentally overwriting
      // an earlier element with a later one
      for( (shiftFrom, (shiftTo, element)) <- data.from(idx).toIndexedSeq.reverseIterator){
        data.remove(shiftFrom)
        data.put(shiftFrom + count, (shiftTo + count, element))
        onRemove(shiftFrom, shiftTo, element)
        onInsert(shiftFrom + count, shiftTo + count, element)
      }    
    }
  }

  def expand(idx: Long, count: Long) =
  {
    if(count > 0){
      val expandee = 
        data.to(idx).lastOption
                    .filter { _._2._1 >= idx }

      expandee match { 
        case Some( (expandeeFrom, (expandeeTo, element)) ) =>
          data.remove(expandeeFrom)
          data.put(expandeeFrom, (expandeeTo+count, element))
          onRemove(expandeeFrom, expandeeTo, element)
          onInsert(expandeeFrom, expandeeTo+count, element)
        case None => ()
      }
      // need to go in descending order to avoid accidentally overwriting
      // an earlier element with a later one.
      // Start from idx + 1 to avoid re-shifting the expandee
      for( (shiftFrom, (shiftTo, element)) <- data.from(idx+1).toIndexedSeq.reverseIterator){
        data.remove(shiftFrom)
        data.put(shiftFrom + count, (shiftTo + count, element))
        onRemove(shiftFrom, shiftTo, element)
        onInsert(shiftFrom + count, shiftTo + count, element)
      }    

    }
  }

  def move(from: Long, to: Long, count: Long) =
  {
    assert(to < from || to >= from+count)

    // these are the elements we're explicitly asked to move
    val explicitlyMovedElements = slice(from, from+count)

    // Create a gap at the target
    // println(s"Moving: ${explicitlyMovedElements.mkString("\n        ")}")
    // println(s"======= Before bisect: \n$this")
    bisect(to)

    // println(s"======= After bisect: \n$this")

    // Elements in the range [low, high+count] are moved right or left, depending on
    // whether from < to or not
    val implicitlyMovedElements = 
      if(from < to){
        data.range(from, to+1).toIndexedSeq.reverseIterator
      } else {
        data.range(to, from+1).toIndexedSeq.iterator
      }
    val implicitOffset = from - to - (if(from < to) { 0 } else { count })

    // The explicitly moved elements are already removed, so start by applying the implicit moves
    for( (shiftFrom, (shiftTo, update)) <- implicitlyMovedElements){
      data.remove(shiftFrom)
      // println(s"Shift $shiftFrom to ${shiftFrom+implicitOffset}")
      data.put(shiftFrom + implicitOffset, (shiftTo + implicitOffset, update))
    }

    // println(this)
    // And finally move the explicitly moved elements to the new range
    val explicitOffset = to - from -  (if(from < to) { count } else { 0 })
    for( (shiftFrom, shiftTo, update) <- explicitlyMovedElements){
      // no need to remove
      // println(s"Reinsert $shiftFrom to ${shiftFrom+explicitOffset}")
      data.put(shiftFrom + explicitOffset, (shiftTo + explicitOffset, update))
    }
    // println(this)

  }

  /**
   * Bisect the map at the specified index (if necessary)
   * @param  idx    The index to bisect at
   * 
   * If a range contains idx, this function guarantees that the the range will be
   * split into the range that ends at idx-1 and a range that starts at idx.
   * 
   * Postcondition
   *   - `data` does not contain a range containing idx, or
   *   - The one range in `data` contained in idx is  
   */
  def bisect(idx: Long): Unit = slice(idx+1, idx) 

  /**
   * Create a "copy" of T to split.  This is mainly here for subclasses to 
   * override in case there is mutable state in the map.
   */
  def cloneElement(t: T): T = t

  /**
   * This function is called whenever an object is inserted into the map for a 
   * specified ragne.
   */
  def onInsert(from: Long, to: Long, element: T): Unit = ()

  /**
   * This function is called whenever an object is inserted into the map for a 
   * specified ragne.
   */
  def onRemove(from: Long, to: Long, element: T): Unit = ()

  /**
   * Retrieve the lowest index
   */
  def min: Long = data.head._1

  /**
   * Retrieve the greatest index
   */
  def max: Long = data.last._2._1

  def iterator: Iterator[(Long, Long, T)] =
    data.iterator
        .map { case (from, (to, element)) => (from, to, element) }


  /**
   * Pretty print
   */
  override def toString(): String = 
  {
    "RangeMap(" + 
      data.map { case (from, (to, elem)) => s"[ $from -> $to ]: $elem"}
          .mkString("\n         ") + ")"
  }
}

object RangeMap
{
  def fillGaps[T](from: Long, to: Long, elements: Seq[(Long, Long, T)]): List[(Long, Long, Option[T])] =
  {
    val nearlyEverything =
      elements
        .map { case x => (x._1, x._2, Some(x._3)) }
        .foldLeft[(List[(Long, Long, Option[T])],Long)]( (Nil, to+1) ) { 
          case ((ret, nextFrom), (currFrom, currTo, currElem)) if currTo < nextFrom-1 =>
            ( (currFrom, currTo, currElem) :: (currTo+1, nextFrom-1, None) :: ret, currFrom )
          case ((ret, _), e) => 
            ( e :: ret, e._1 )
        }
        ._1

    // println(s"nearlyEverything = $nearlyEverything")
    if(nearlyEverything.isEmpty){ List( (from, to, None) ) }
    else if(nearlyEverything.head._1 > from){ (from, to, None) :: nearlyEverything }
    else { nearlyEverything }

  }


  implicit val tmValueFormat = Json.format[(Long, UpdateRule)]
  implicit val rangeMapWrites = new Writes[RangeMap[UpdateRule]] {
    def writes(rM: RangeMap[UpdateRule]): JsValue  = {
      val map = mutable.Map.empty[String, JsValue]
      for((k, v) <- rM.data) {
        map(k.toString()) = Json.toJson(v)
      }
      val returnThis: JsObject = new JsObject(map)
      return returnThis
    }
  }
  implicit val rangeMapReads = new Reads[RangeMap[UpdateRule]] {
    def reads(j: JsValue): JsResult[RangeMap[UpdateRule]] = {
      val data = j.as[Map[String, JsValue]]
      val returnThis = new RangeMap[UpdateRule]
      for((k, v) <- data) {
        returnThis.data(k.toLong) = data(k).as[(Long, UpdateRule)]
      }
      JsSuccess(returnThis)
    }
  }


  implicit val rangeMapFormat: Format[RangeMap[UpdateRule]] = Format(rangeMapReads, rangeMapWrites)

}