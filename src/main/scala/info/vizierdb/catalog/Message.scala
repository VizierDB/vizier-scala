package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.types._

case class Message(
  val resultId: Identifier,
  val mimeType: String,
  val data: Array[Byte]
)
{
  def dataString: String = new String(data)
}
object Message 
  extends SQLSyntaxSupport[Message]
{
  def apply(rs: WrappedResultSet): Message = autoConstruct(rs, (Message.syntax).resultName)
  override def columns = Schema.columns(table)
}