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

  def closeAfter[T](c: InputStream)(op: InputStream => T): T =
    { val ret = op(c); c.close(); ret }

  def closeAfter[T](c: OutputStream)(op: OutputStream => T): T =
    { val ret = op(c); c.close(); ret }
}

