package info.vizierdb.util

import java.io.File
import java.net.URLConnection
import info.vizierdb.types.MIME

object FileUtils {
  def recursiveDelete(file: File): Unit =
  {
    if(file.isDirectory()){
      file.listFiles.foreach { recursiveDelete(_) }
    }
    file.delete
  }

  def guessMimeType(file: File, default: String = "text/plain"): String =
    file.getName.split("\\.").last match {
      case "js"   => MIME.JAVASCRIPT
      case "md"   => MIME.MARKDOWN
      case "json" => MIME.JSON
      case _ => Option(
                  URLConnection.guessContentTypeFromName(file.getName())
                ).getOrElse { default }
    }

}