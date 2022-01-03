package info.vizierdb.ui.components.dataset

import rx._
import info.vizierdb.serialized.DatasetRow
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class RowCache(
  var fetchRows: (Long, Int) => Future[Seq[DatasetRow]],
  selectForInvalidation: Seq[Long] => Long
)(implicit owner: Ctx.Owner)
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
          fetchRows(pageIdx, BUFFER_PAGE).map { _.toArray }
        )
    }
    return cache(pageIdx)
  }

  def invalidate(pageIdx: Long) = 
    cache.remove( pageIdx )

  def pageOf(idx: Long) = (idx - (idx % BUFFER_PAGE)).toLong

  def apply(idx: Long): Rx[Option[DatasetRow]] = 
    cache(pageOf(idx))(idx)

  class CachePage(start: Long)
  {
    def end = start + BUFFER_PAGE
    def pageId = pageOf(start)

    val data = Var[Option[Array[DatasetRow]]](None)

    def apply(idx: Long): Rx[Option[DatasetRow]] =
      Rx { data().map { _((idx - start).toInt) } }

    def this(start: Long, dataFuture: Future[Array[DatasetRow]])
    {
      this(start)
      dataFuture.onComplete { 
        case ex:Failure[_] => invalidate(start)
        case Success(rows) => data() = Some(rows)
      }
    }
  }
}
