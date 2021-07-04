package info.vizierdb.test

import scala.concurrent.Promise
import scala.concurrent.Future
import scala.util.Success
import scala.util.Try

case class MockPromise[T](result: T) extends Promise[T]
{
  def future: Future[T] = MockFuture(Success(result))
  def isCompleted: Boolean = true
  def tryComplete(result: Try[T]): Boolean = 
    throw new UnsupportedOperationException("Mock promises can't be completed")
}