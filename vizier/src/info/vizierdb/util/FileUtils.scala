package info.vizierdb.util

import java.io.File

object FileUtils {
  def recursiveDelete(file: File): Unit =
  {
    if(file.isDirectory()){
      file.listFiles.foreach { recursiveDelete(_) }
    }
    file.delete
  }

}