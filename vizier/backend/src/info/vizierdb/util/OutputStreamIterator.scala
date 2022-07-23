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