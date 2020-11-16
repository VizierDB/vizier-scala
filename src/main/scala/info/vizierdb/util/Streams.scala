package info.vizierdb.util

import java.io.{InputStream, OutputStream, BufferedInputStream}

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
}