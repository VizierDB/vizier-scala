package info.vizierdb.spreadsheet

import scala.collection.mutable

/**
 * This class acts as a sort of 
 */
class Executor
{
  var activeRanges = RangeSet()
  val updates = mutable.Map[Long, UpdateRule]()
  var nextUpdate = 0l

  


}