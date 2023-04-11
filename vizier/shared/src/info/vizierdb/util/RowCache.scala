package info.vizierdb.util

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.reflect.ClassTag

/** 
 * A bounded-size caching wrapper around a Seq[T]
 *
 * @param  fetchRows              Initiate retrieval of a subset of the rows.  
 *                                The parameters are the offset and limit, 
 *                                respectively.
 * @param  selectForInvaildation  Given a sequence of start indices, select one
 *                                to be invalidated.
 *
 * Although it seems like an ideal candidate for the Rx system, Rx seems like it
 * will create a huge mess here, both from a runtime and a code complexity 
 * standpoint.
 * 
 * In the overall system, we have a single global state object that gets updated
 * over time:
 * (i)   The state object is a single, canonical, fully-materialized source of 
 *       truth.  The entire Rx tree can be created and forgotten about.
 * (ii)  There are lots of moving parts that may be interdepdendent.  Rx helps
 *       here, because it decouples the code updating the state from the code
 *       displaying the state.
 * (iii) The structure of the state object is relatively static.  Nodes are 
 *       added/removed, but this is a result of actual state changes. This means
 *       that we're not creating Rx nodes, and we don't need to worry about 
 *       context leaking when an Rx node's owner is deallocated.
 * 
 * Here:
 * (i)   There are very few moving parts, and all of them deal with the same 
 *       component: the spreadsheet
 * (ii)  The source of truth is not fully materialized -- most of the dataset 
 *       lives on the server.  
 * (iii) Nodes are added/removed quickly every time the user scrolls.
 * 
 * As a result, the spreadsheet view uses callbacks instead of Rx.
 */
class RowCache[T](
  var fetchRows: (Long, Int) => Future[Seq[T]],
  var selectForInvalidation: (Seq[Long], Int) => Long
)(implicit tag: ClassTag[T], ec: scala.concurrent.ExecutionContext)
{
  /**
   * The maximum number of records per page.  
   */
  val BUFFER_PAGE = 1000
  /**
   * The maximum number of pages to cache.
   */
  val BUFFER_SIZE = 20

  /**
   * The actual cache
   */
  val cache = mutable.LinkedHashMap[Long, CachePage]()

  /**
   * Refresh callbacks.
   * 
   * Whenever the available data in a range changes (e.g., because a load 
   * completed), this callback will be called with (Offset, Limit)
   */
  val onRefresh = mutable.ArrayBuffer[(Long, Int) => Unit]()

  /**
   * Retrieve the page starting at the provided index, or create a cache entry 
   * if the page is not already cached.
   * 
   * @param  pageIdx     The first index of the page.  MUST be aligned to BUFFER_PAGE.
   */
  def page(pageIdx: Long): CachePage = 
  {
    assert(pageIdx % BUFFER_PAGE == 0, "Start index not aligned on a buffer page")
    if(!cache.contains(pageIdx)){
      if(cache.size >= BUFFER_SIZE) {
        invalidate(selectForInvalidation(cache.keys.toSeq, BUFFER_PAGE))
      }
      cache(pageIdx) = 
        new CachePage(
          pageIdx,
          
        )
    }
    return cache(pageIdx)
  }

  /**
   * Provide data to pre-cache in the index
   * 
   * @param  data        The data to pre-cache.  This may be of any size that 
   *                     will fit in the cache.
   * @param  startIdx    (optional) The index of the first record in the cache.
   *                     This MUST be aligned to BUFFER_PAGE
   * 
   * If data % BUFFER_PAGE != 0, the last pre-cached page will be marked as a 
   * "partial" page.  If records falling outside of the pre-cached region are 
   * requested, the system will kick off a request for the remaining pages 
   * records.
   */
  def preload(data: Seq[T], startIdx: Long = 0)
  {
    assert(data.size < BUFFER_SIZE * BUFFER_PAGE, "Preloading more data than will fit in cache")
    assert(startIdx % BUFFER_PAGE == 0, "Start index not aligned on a buffer page")

    val pages = data.grouped(BUFFER_PAGE)

    while(cache.size + pages.size >= BUFFER_SIZE){
      invalidate(selectForInvalidation(cache.keys.toSeq, BUFFER_PAGE))
    }

    for((pageData, idx) <- data.grouped(BUFFER_PAGE).zipWithIndex){
      val page = new CachePage(idx * BUFFER_PAGE + startIdx, pageData.toArray)
      cache.put(page.start, page)
    }
  }

  /**
   * Remove the page starting at the provided index from the cache
   */
  def invalidate(pageIdx: Long) = 
    cache.remove( pageIdx )

  /**
   * Find the identifier of the page of rhte specified row index.  This is the
   * next lowest row index that is aligned with BUFFER_PAGE
   */
  def pageOf(idx: Long) = (idx - (idx % BUFFER_PAGE)).toLong

  /**
   * Retrieve the row at the specified index, or None if the row has not been
   * loaded yet.
   */
  def apply(idx: Long): Option[T] = 
    page(pageOf(idx))(idx)

  /**
   * A wrapper around one page of cached data
   * @param  start      The first index of the data page.
   */
  class CachePage(val start: Long)
  {
    /**
     * 1+the last index of the data page
     */
    def end = start + BUFFER_PAGE

    /**
     * The page index of this cache page
     */
    def pageIdx = start

    /**
     * True if this the data on this page is not a full page.  This can happen 
     * as, e.g., a consequence of preloading data (e.g., the first 30 rows of a 
     * dataset are cached as part of a dataset message).
     */
    def partial = !fetched && data.map { _.size < BUFFER_PAGE }.getOrElse { false }

    /**
     * True if the data on this page has been loaded fully at least once.
     * 
     * We use this information, e.g., to mark the last page in a dataset as 
     * non-partial
     */
    var fetched = false

    /**
     * True if this page is actively being fetched.
     */
    var refreshing = false

    /**
     * The actual data for this page.  It has three states:
     * data.isEmpty                -> Data has not been loaded yet.
     * data.isDefined && partial   -> Partial data has been preloaded, but is 
     *                                incomplete.  Requests to the preloaded
     *                                rows will return Some(), while requests
     *                                to non-preloaded rows will return None
     *                                and trigger a full load.
     * data.isDefined && !partial  -> Data has been fully loaded.  All requests
     *                                will return Some()
     */
    var data: Option[Array[T]] = None

    /**
     * Start loading the data in this cell if necessary.  This is a no-op if
     * the cell is already being loaded.
     */
    def tryToLoadData(): Unit = 
    {
      if(refreshing){ return }
      refreshing = true
      updateWhenReady(fetchRows(start, BUFFER_PAGE).map { _.toArray })
    }

    /**
     * Register a handler for when data loading completes
     */
    def updateWhenReady(dataFuture: Future[Array[T]]): Unit =
      dataFuture.onComplete { 
        case ex:Failure[_] => 
        {
          refreshing = false
          invalidate(pageIdx) // if we had a load error, invalidate this page
        }
        case Success(rows) => 
        {
          refreshing = false 
          data = Some(rows)
          fetched = true
          onRefresh.foreach { _(start, BUFFER_PAGE) }
        }
      }

    /**
     * Retrieve the specified row from this page. 
     * @param   idx     The index of the row to retrieve.  Must be in [start, start+BUFFER_PAGE)
     */
    def apply(idx: Long): Option[T] =
    {
      def localIdx = (idx - start).toInt
      assert(localIdx >= 0)
      assert(localIdx < BUFFER_PAGE)
      println(s"Fetch: $idx -> $localIdx (partial = $partial; size = ${data.map { _.size.toString }.getOrElse { "unloaded" }})")
      if(data.isEmpty || (partial && data.get.size <= localIdx)){ 
        // If this is a partial or data hasn't been loaded, trigger a full load
        tryToLoadData()
        return None
      } else {
        // If we have the data, return it
        assert(localIdx < data.get.size)
        return Some(data.get(localIdx))
      }
    }

    /**
     * Initialize the page with a data load already in-progress
     * @param  start      The first index of the data page.
     * @param  dataFuture A future wrapping the data for this page.
     */
    def this(start: Long, dataFuture: Future[Array[T]])
    {
      this(start)
      updateWhenReady(dataFuture)
    }

    /**
     * Initialize the page with a precached dataset
     * @param  start      The first index of the data page.
     * @param  data       A (potentially partial) chunk of data to put at this 
     *                    page.
     */
    def this(start: Long, data: Array[T])
    {
      this(start)
      this.data = Some(data)
    }
  }
}
