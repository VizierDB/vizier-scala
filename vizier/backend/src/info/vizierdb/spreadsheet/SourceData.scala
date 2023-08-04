package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.StructField
import scala.concurrent.Future
import info.vizierdb.util.RowCache
import scala.collection.mutable
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext
import scala.util.Random

sealed trait SpreadsheetDataSource
{
  def schema: Array[StructField]
  def size: Future[Long]
  def apply(col: Int, row: Long): Future[Any]
}

case class InlineSource(
  schema: Array[StructField],
  data: Array[Array[Any]]
) extends SpreadsheetDataSource
{
  def size = Future.successful(data.size.toLong)
  def apply(col: Int, row: Long) = Future.successful(data(row.toInt)(col))
}

case class CachedSource(
  schema: Array[StructField],
  rows: RowCache[Array[Any]],
  size: Future[Long]
)(implicit ec: ExecutionContext) extends SpreadsheetDataSource
{
  val pendingRows = mutable.Map[Long, Promise[Array[Any]]]()

  rows.onRefresh.append { (offset, limit) =>
    synchronized {

      val readyRows = pendingRows.keys.toIndexedSeq.filter { k => k >= offset && k < offset+limit }
      for(row <- readyRows)
      {
        val data = rows(row) match {
          case None => // odd... still not ready
          case Some(data) =>
            pendingRows.remove(row).foreach { _.success(data) }
        }
      }      
    }
  }

  def apply(col: Int, row: Long): Future[Any] =
    synchronized {
      rows(row) match {
        case None => 
          pendingRows.getOrElseUpdate(row, Promise[Array[Any]]())
                     .future.map { _(col) }
        case Some(data) => Future.successful(data(col))
      }
    }

}