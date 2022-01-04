package info.vizierdb.ui.components.dataset

import rx._
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.reflect.ClassTag

class RowCache[T](
  var fetchRows: (Long, Int) => Future[Seq[T]],
  selectForInvalidation: Seq[Long] => Long
)(implicit owner: Ctx.Owner, tag: ClassTag[T])
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val BUFFER_PAGE = 1000
  val BUFFER_SIZE = 20

  private val cache = mutable.Map[Long, CachePage]()

  def page(pageIdx: Long): CachePage = 
  {
    if(!cache.contains(pageIdx)){
      if(cache.size >= BUFFER_SIZE) {
        invalidate(selectForInvalidation(cache.keys.toSeq))
      }
      cache(pageIdx) = 
        new CachePage(
          pageIdx,
          
        )
    }
    return cache(pageIdx)
  }

  def preload(data: Seq[T], startIdx: Long = 0)
  {
    assert(data.size < BUFFER_SIZE * BUFFER_PAGE, "Preloading more data than will fit in cache")
    assert(startIdx % BUFFER_PAGE == 0, "Start index not aligned on a buffer page")

    val pages = data.grouped(BUFFER_PAGE)

    while(cache.size + pages.size >= BUFFER_SIZE){
      invalidate(selectForInvalidation(cache.keys.toSeq))
    }

    for((pageData, idx) <- data.grouped(BUFFER_PAGE).zipWithIndex){
      val page = new CachePage(idx * BUFFER_PAGE + startIdx, pageData.toArray)
      cache.put(page.start, page)
    }
  }

  def invalidate(pageIdx: Long) = 
    cache.remove( pageIdx )

  def pageOf(idx: Long) = (idx - (idx % BUFFER_PAGE)).toLong

  def apply(idx: Long): Rx[Option[T]] = 
    cache(pageOf(idx))(idx)

  class CachePage(val start: Long)
  {
    def end = start + BUFFER_PAGE
    def pageId = pageOf(start)
    def partial = !fetched && data.now.map { _.size < BUFFER_PAGE }.getOrElse { false }
    var fetched = false
    var refreshing = false

    val data = Var[Option[Array[T]]](None)

    def refresh(): Unit = 
    {
      if(refreshing){ return }
      refreshing = true
      updateWhenReady(fetchRows(start, BUFFER_PAGE).map { _.toArray })
    }

    def apply(idx: Long): Rx[Option[T]] =
    {
      def localIdx = (idx - start).toInt
      // If this is a partial, trigger a full load
      if(partial && data.now.map { _.size <= localIdx }.getOrElse(true)) { refresh() }
      Rx { data().flatMap { rows => if(localIdx >= rows.size) { None }
                                    else { Some(rows(localIdx)) } } }
    }

    def updateWhenReady(dataFuture: Future[Array[T]]): Unit =
      dataFuture.onComplete { 
        case ex:Failure[_] => //refreshing = false; refresh()
        case Success(rows) => refreshing = false; data() = Some(rows); fetched = true
      }

    def this(start: Long, dataFuture: Future[Array[T]])
    {
      this(start)
      updateWhenReady(dataFuture)
    }

    def this(start: Long, data: Array[T])
    {
      this(start)
      this.data() = Some(data)
    }
  }
}
