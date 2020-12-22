package info.vizierdb.util

import java.io.{InputStream, OutputStream, BufferedInputStream}
import scala.collection.mutable.Buffer

object Streams
{
  def cat(source: InputStream, sink: OutputStream)
  {
    val in = new BufferedInputStream(source)
    val buffer = new Array[Byte](10*1024)
    var readSize = in.read(buffer, 0, buffer.size)
    while(readSize >= 0){
      if(readSize > 0){
        sink.write(buffer, 0, readSize)
      } else { Thread.sleep(100) }
      readSize = in.read(buffer, 0, buffer.size)
    }
  }

  def readAll(source: InputStream): Array[Byte] =
  {
    var b = source.read()
    val buffer = Buffer[Byte]()
    while(b >= 0){
      buffer.append(b.toByte)
      b = source.read()
    }
    buffer.toArray
  }
}