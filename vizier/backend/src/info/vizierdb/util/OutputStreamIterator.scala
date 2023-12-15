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
package info.vizierdb.util

import java.io.OutputStream
import akka.util.ByteString
import java.util.concurrent.Semaphore
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean

class OutputStreamIterator
  extends OutputStream
{

  val bufferEmpty = new Semaphore(1)
  val bufferFull = new Semaphore(0)
  var buffer: Array[Byte] = null

  val closed = new AtomicBoolean(false)


  def write(x: Int): Unit = 
    write(new Array[Byte](x.toByte))
  override def write(x: Array[Byte], start: Int, end: Int): Unit = 
    write(Arrays.copyOfRange(x, start, end))
  override def write(x: Array[Byte]): Unit = 
  {
    bufferEmpty.acquire()
    synchronized {
      buffer = x
      bufferFull.release()
    }
  }

  override def close(): Unit = 
    closed.set(true)

  object iterator
    extends Iterator[ByteString]
  {
    def hasNext: Boolean = 
    {
      synchronized {
        !closed.get || bufferFull.availablePermits() > 0
      }
    }

    def next(): ByteString =
    {
      bufferFull.acquire()
      synchronized {
        val ret = buffer
        buffer = null
        bufferEmpty.release()
        ByteString(ret)
      }
    }
  }

}