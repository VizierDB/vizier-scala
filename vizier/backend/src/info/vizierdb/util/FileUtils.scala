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