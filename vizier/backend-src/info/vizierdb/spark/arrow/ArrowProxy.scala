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