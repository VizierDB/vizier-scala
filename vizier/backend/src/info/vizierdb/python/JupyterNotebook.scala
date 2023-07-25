package info.vizierdb.python
import play.api.libs.json._


case class JupyterCell(
   cell_type: String,
   source: Seq[String]
)

object JupyterCell
{
  implicit val format: Format[JupyterCell] = Json.format
}

case class JupyterNotebook(
  cells: Seq[JupyterCell]
)

object JupyterNotebook
{
  implicit val format: Format[JupyterNotebook] = Json.format
}