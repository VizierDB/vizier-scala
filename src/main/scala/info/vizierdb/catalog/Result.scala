package info.vizierdb.catalog

import scalikejdbc._
import java.time.ZonedDateTime
import info.vizierdb.types._

case class Result(
  id: Identifier,
  started: ZonedDateTime,
  finished: Option[ZonedDateTime],
)
{
  def addMessage(message: String)(implicit session: DBSession):Unit = 
    addMessage("text/plain", message.getBytes())
  def addMessage(mimeType: String, data: Array[Byte])(implicit session: DBSession):Unit = 
    withSQL { 
      val m = Message.column
      insertInto(Message)
        .namedValues(
          m.resultId -> id,
          m.mimeType -> mimeType,
          m.data -> data
        )
    }.update.apply()
  def addOutput(userFacingName: String, artifactId: Identifier)(implicit session: DBSession): Unit =
  {
    withSQL {
      val o = OutputArtifactRef.column
      insertInto(OutputArtifactRef)
        .namedValues(
          o.resultId -> id,
          o.userFacingName -> userFacingName,
          o.artifactId -> artifactId
        )
    }.update.apply()
  }
  def addInput(userFacingName: String, artifactId: Identifier)(implicit session: DBSession): Unit = 
  {
    withSQL {
      val i = InputArtifactRef.column
      insertInto(InputArtifactRef)
        .namedValues(
          i.resultId -> id,
          i.userFacingName -> userFacingName,
          i.artifactId -> artifactId
        )
    }.update.apply()
  }
}
object Result
  extends SQLSyntaxSupport[Result]
{
  def apply(rs: WrappedResultSet): Result = autoConstruct(rs, (Result.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(target: Identifier)(implicit session:DBSession): Result = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Result] = 
    withSQL { 
      val b = Result.syntax 
      select
        .from(Result as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()
}