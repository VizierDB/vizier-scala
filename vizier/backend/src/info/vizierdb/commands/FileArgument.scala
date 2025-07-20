/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.commands

import play.api.libs.json._
import scalikejdbc._
import info.vizierdb.types.Identifier
import info.vizierdb.filestore.Filestore
import java.net.URL
import info.vizierdb.catalog.Artifact

case class FileDescription(
  preview: Option[String]
)
object FileDescription
{
  implicit val format: Format[FileDescription] = Json.format
}

case class FileArgument(
  fileid: Option[Identifier] = None,
  filename: Option[String] = None,
  file: Option[FileDescription] = None,
  url: Option[String] = None
)
{
  def preview: Option[String] = file.flatMap { _.preview }
  def validate: Option[String] = 
    if(fileid.isEmpty && url.isEmpty){
      Some("Expecting either url or fileid")
    } else { None }

  /**
   * Get a path to the referenced file argument.
   * @param project         The project in which to look for the file
   * @param noRelativePaths Require that the returned file use an absolute path
   * @return                The path to the file, and a boolean that is true if
   *                        the returned path should be treated as relative to the
   *                        data directory.
   */
  def getPath(projectId: Identifier, noRelativePaths: Boolean = false): (String, Boolean) =
    url.map { (_, false) }
       .orElse { 
         fileid.map { fileId => 
           if(noRelativePaths) { 
             (Filestore.getAbsolute(projectId, fileId).toString, false)
           } else {
             (Filestore.getRelative(projectId, fileId).toString, true)
           }
         }
       }.getOrElse { 
         throw new IllegalArgumentException("Need at least one of fileid or url")
       }

  def needsStaging: Boolean =
    url.map { 
      case u if u.startsWith("http://") 
             || u.startsWith("https://") => true
      case _ => false
    }.getOrElse { false }

  def guessFilename(projectId: Option[Identifier] = None)(implicit session:DBSession) =
    filename.getOrElse {
      url.map { _.split("/").last }
         .orElse { 
            fileid.flatMap { fileId =>
              val properties = Artifact.get(fileId, projectId).json
              (properties \ "filename").asOpt[String]
            } 
          }
         .getOrElse { "<unknown filename>" }
    }

  def withGuessedFilename(projectId: Option[Identifier] = None)(implicit session:DBSession) = 
    if(filename.isDefined){ this }
    else { copy(filename = Some(guessFilename(projectId))) }

  override def toString: String =
  {
    val rel = 
      url.map { "url '"+_+"'" }
         .orElse { fileid.map { "artifact " + _ } }
    return  (filename, rel) match {
      case (None, None) => "<unknown file>"
      case (Some(name), None) => name
      case (None, Some(source)) => source
      case (Some(name), Some(source)) => s"$source ($name)"  
    }
  }

}
object FileArgument
{
  implicit val format: Format[FileArgument] = Json.format

  /**
   * Parse a file argument, applying a decoder to the fileid (for import)
   */
  def apply(j: JsValue, decodeFileId: String => Identifier): FileArgument = 
  {
    FileArgument(
      (j \ "fileid").asOpt[String]
                    .orElse { (j \ "fileid").asOpt[Long]
                                            .map { x:Long => x.toString } }
                    .map { decodeFileId },
      (j \ "filename").asOpt[String],
      (j \ "file").asOpt[FileDescription],
      (j \ "url").asOpt[String]
    )
  }

  def fromUrl(url: String, filename: String = null) = 
    new FileArgument(url = Some(url), filename = Option(filename))
}

