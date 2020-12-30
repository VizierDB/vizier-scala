/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.types.Identifier
import info.vizierdb.filestore.Filestore

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
  def getPath(projectId: Identifier): String =
    url.getOrElse { 
      fileid.map { Filestore.get(projectId, _).toString }
      .getOrElse { 
        throw new IllegalArgumentException("Need at least one of fileid or url")
      }
    }
  override def toString =
    filename
      .orElse(fileid.map { _.toString })
      .getOrElse { "<unknown file>" }

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
      (j \ "fileid").asOpt[String].map { decodeFileId },
      (j \ "filename").asOpt[String],
      (j \ "file").asOpt[FileDescription],
      (j \ "url").asOpt[String]
    )
  }
}

