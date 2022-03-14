package info.vizierdb.spark

import org.apache.spark.sql.{ Row, DataFrame }
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import scala.collection.concurrent.TrieMap
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.TimerUtils.logTime

/**
 * A simple LRU cache for dataframes
 * 
 * Typical interactions are: 
 *   DataFrameCache(tableName, df)(start, end)
 */
object DataFrameCache
  extends LazyLogging
{
  val SEQUENCE_ATTRIBUTE = "__VIZIER_CACHE_SEQ"

  /**
   * The number of rows in a buffer page
   */
  val BUFFER_PAGE = {
    Option(System.getenv("DATAFRAME_CACHE_PAGE_ROWS")) match {
      case None | Some("") => 10000
      case Some(str) => str.toLong
    }
  }
  /**
   * The number of pages cached before we start evictions
   */
  val BUFFER_SIZE = {
    Option(System.getenv("DATAFRAME_CACHE_PAGE_SIZE")) match {
      case None | Some("") => 100
      case Some(str) => str.toLong
    }
  }

  /**
   * The actual cache
   */
  private val cache = TrieMap[String, CachedDataFrame]()

  /**
   * The user-facing lookup function
   */
  def apply(table: String)(df: => DataFrame): CachedDataFrame =
  {
    cache.getOrElseUpdate(table, { 
      new CachedDataFrame(
        AnnotateWithSequenceNumber(df, attribute = SEQUENCE_ATTRIBUTE),
        table,
        df.schema.fieldNames
      ) 
    })
  }

  def get(table: String): Option[CachedDataFrame] =
    cache.get(table)

  /**
   * Align start/end to page boundaries
   *
   * TODO: Enable some sort of prefetching, by expanding the range 
   * if start is close to end?
   */
  def alignRange(start: Long, end: Long): (Long, Long) =
  {
    (
      start - (start % BUFFER_PAGE), 

      end + (if(end % BUFFER_PAGE == 0){ 0 }
             else { (BUFFER_PAGE - (end % BUFFER_PAGE)) })
    )
  }

  /**
   * Check if the buffer is too big and release cache entries if so.
   *
   * The `target` parameter is guaranteed to remain in the cache
   * after this is invoked
   */
  def updatePageCount(target: CachedDataFrame)
  {
    // Tally up the number of pages allocated in the cache
    val pages = cache.values.map { _.pages }.sum

    // If we need to free...
    if(pages > BUFFER_SIZE){
      var freed = 0l
      // Simple LRU: Iterate over the elements in order of last access
      for((table, entry) <- cache.toSeq.sortBy { -_._2.lastAccessed }){
        // Keep freeing until we've freed enough pages.
        if(pages - freed > BUFFER_SIZE){ 
          // Make sure that we don't free the one thing that we're updating
          if(entry != target){
            // Remove the table from the cache
            cache.remove(table)
            // And register the pages that we've freed
            freed += entry.pages
          }
        }
      }
    }
    // In the unlikely event that we were asked to buffer more than the buffer
    // size, it'll still be in the cache... but the buffer size should be big
    // enough to prevent that.
  }

  def countPages(start: Long, end: Long): Long =
  {
    ((end - start) / BUFFER_PAGE) + 
      (if((end-start) % BUFFER_PAGE > 0) { 1 } else { 0 })
  }

  def invalidate(): Unit =
    cache.clear()
}


class CachedDataFrame(val df: DataFrame, table: String, fields: Seq[String])
  extends LazyLogging
{
  var lastAccessed = System.currentTimeMillis()
  var bufferStart = 0l
  var buffer: Array[Row] = Array()
  var computedSize = -1l
  
  def bufferEnd = bufferStart + buffer.size

  def apply(start: Long, end: Long): Array[Row] = 
  {
    logger.trace(s"$table($start, $end) w/ buffer @ [$bufferStart, $bufferEnd)")
    if(!isBuffered(start, end)){ 
      rebuffer(start, end)
    }
    lastAccessed = System.currentTimeMillis()
    logger.trace(s"$table($start, $end) w/ slice: [${start-bufferStart}, ${end-bufferStart})")
    buffer.slice(
      (start - bufferStart).toInt, 
      (end - bufferStart).toInt
    )
  }

  def size: Long =
  {
    if(computedSize < 0){ computedSize = df.count() }
    return computedSize
  }

  def collect(): Array[Row] = 
  {
    logger.trace(s"$table.collect()")
    if(size < 0 || !isBuffered(0, size)){ bufferAll() }
    return buffer
  }

  def isBuffered(start: Long, end: Long) =
    (start >= bufferStart && end <= bufferEnd)

  def bufferAll()
  {
    logger.trace(s"Buffering complete $table")
    buffer = df.collect()
    bufferStart = 0
    DataFrameCache.updatePageCount(this)
  }

  def rebuffer(start: Long, end: Long)
  {
    logger.trace(s"Rebuffering $table for $start - $end")
    val (targetStart, targetEnd) = DataFrameCache.alignRange(start, end)
    logger.trace(s"Rebuffering $table: [$start, $end) -> [$targetStart,$targetEnd)")
    // logTime(s"REBUFFER[$table/$start,$end]", df.toString()) {
      buffer = df.filter(
                    (df(DataFrameCache.SEQUENCE_ATTRIBUTE) >= targetStart)
                      and
                    (df(DataFrameCache.SEQUENCE_ATTRIBUTE) < targetEnd)
                  )
                 .collect()
    // }
    logger.trace(s"Rebuffering: buffer for $table now has ${buffer.size} rows")
    bufferStart = targetStart
    DataFrameCache.updatePageCount(this)
  }

  def pages = DataFrameCache.countPages(bufferStart, bufferEnd)
}
