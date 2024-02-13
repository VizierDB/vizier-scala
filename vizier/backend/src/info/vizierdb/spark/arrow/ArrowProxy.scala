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
package org.apache.spark.sql

import org.apache.spark.sql.execution.arrow.ArrowConverters
import org.apache.spark.sql.catalyst.InternalRow
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.spark.sql.util.ArrowUtils
import org.apache.arrow.memory.RootAllocator

object ArrowProxy {
  
  def toArrow(df:DataFrame)  = {
    //ArrowConverters.toBatchIterator(df.toLocalIterator().asInstanceOf[Iterator[InternalRow]], df.schema, 100, "EST", org.apache.spark.TaskContext.get() )
    df.collectAsArrowToPython
  }
  
  val allocator = new RootAllocator(Integer.MAX_VALUE);
  
  def writeToMemoryFile(file:String, df:DataFrame): (Int, String) = {
    val arrowOut = toArrow(df)
    /*val bytesWritten = 0;
    val schema = ArrowUtils.toArrowSchema(df.schema, "EST")
    val rafile = new RandomAccessFile(file, "rw")
    val out = rafile.getChannel()
    //.map(FileChannel.MapMode.READ_WRITE, 0, 2048);
    val root = VectorSchemaRoot.create(schema, allocator)
    rafile.write(arrowOut)
    println(s"toArrow: ${arrowOut.map(el => s"${el.getClass} -> $el" ).mkString("\n", "\n", "\n")}")
    val writer = new ArrowStreamWriter(root, null, out)
    writer.start();
    writer.writeBatch();
    writer.end();
    val totalBytesWritten = writer.bytesWritten();
    println(totalBytesWritten)*/
    (arrowOut(0).asInstanceOf[Int], arrowOut(1).asInstanceOf[String])
  }
  
}