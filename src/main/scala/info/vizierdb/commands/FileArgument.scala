package info.vizierdb.commands

import play.api.libs.json._

case class FileDescription(
  preview: Option[String]
)
object FileDescription
{
  implicit val format: Format[FileDescription] = Json.format
}

case class FileArgument(
  fileid: Option[String] = None,
  filename: Option[String] = None,
  file: Option[FileDescription] = Some(FileDescription(None)),
  url: Option[String] = None
)
{
  def preview: Option[String] = file.flatMap { _.preview }
  def validate: Option[String] = 
    if(fileid.isEmpty && url.isEmpty){
      Some("Expecting either url or fileid")
    } else { None }
  def getPath =
    url.getOrElse { 
      throw new UnsupportedOperationException("Uploaded Files Not Supported Yet")
    }
}
object FileArgument
{
  implicit val format: Format[FileArgument] = Json.format
}