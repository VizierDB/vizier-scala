package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.types._
import java.time.ZonedDateTime
import info.vizierdb.catalog.binders._

case class Artifact(
  id: Identifier,
  t: ArtifactType.T,
  created: ZonedDateTime,
  data: Array[Byte]
)
{
  def string = new String(data)
  def nameInBackend = Artifact.nameInBackend(t, id)
}
object Artifact
  extends SQLSyntaxSupport[Artifact]
{
  def apply(rs: WrappedResultSet): Artifact = autoConstruct(rs, (Artifact.syntax).resultName)
  override def columns = Schema.columns(table)

  def make(t: ArtifactType.T, data: Array[Byte])(implicit session: DBSession): Artifact =
  {
    val artifactId = withSQL {
      val a = Artifact.column
      insertInto(Artifact)
        .namedValues(
          a.t -> t,
          a.created -> ZonedDateTime.now(),
          a.data -> data
        )
    }.updateAndReturnGeneratedKey.apply()
    Artifact.get(artifactId)
  }

  def get(target: Identifier)(implicit session:DBSession): Artifact = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Artifact] = 
    withSQL { 
      val b = Artifact.syntax 
      select
        .from(Artifact as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()

  def nameInBackend(t: ArtifactType.T, artifactId: Identifier): String =
    s"${t}_${artifactId}"
}