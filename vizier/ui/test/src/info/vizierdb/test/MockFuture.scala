/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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