package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.binders._

case class Message(
  val resultId: Identifier,
  val mimeType: String,
  val data: Array[Byte],
  val stream: StreamType.T
)
{
  def dataString: String = new String(data)

  def describe: JsObject = 
    Json.obj(
      "type" -> mimeType,
      "value" -> (mimeType match {
        case MIME.DATASET_VIEW => Json.parse(data)
        case _ => println("MIME: " +mimeType); JsString(new String(data))
      })
    )
}
object Message 
  extends SQLSyntaxSupport[Message]
{
  def apply(rs: WrappedResultSet): Message = autoConstruct(rs, (Message.syntax).resultName)
  override def columns = Schema.columns(table)
}