package info.vizierdb.test

import scala.concurrent.{ Future, Promise }
import scala.concurrent.CanAwait
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.util.{ Try, Success, Failure }

case class MockFuture[+T](result: Try[T]) extends Future[T]
{
  override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = 
    return this
  override def result(atMost: Duration)(implicit permit: CanAwait): T = 
    result.get
  override def isCompleted: Boolean = true
  override def onSuccess[U](pf: PartialFunction[T,U])(implicit executor: ExecutionContext): Unit = 
  {
    onComplete { 
      case Success(t) => pf(t)
      case Failure(e) => println(s"MockFuture($result) FAILED UNEXPECTEDLY: $e")
    }
  }
  override def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit = 
    f(result)
  override def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S] = 
    new MockFuture(f(result))
  override def transformWith[S](f: Try[T] => Future[S])(implicit executor: ExecutionContext): Future[S] = 
    new MockFuture[S](
      f(result) match {
        case MockFuture(result) => result
      }
    )
  override def value: Option[Try[T]] = Some(result)
}
object MockFuture
{
  def apply[T](result: T) = new MockFuture[T](Success(result))
}