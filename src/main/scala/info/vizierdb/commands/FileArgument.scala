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
}
object FileArgument
{
  implicit val format: Format[FileArgument] = Json.format
}