package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.types._
import java.time.ZonedDateTime
import info.vizierdb.catalog.binders._

case class ArtifactRef(
  val resultId: Identifier,
  val artifactId: Option[Identifier],
  val userFacingName: String
){ 
  def get(implicit session: DBSession): Option[Artifact] =
    artifactId.map { Artifact.get(_) }

  def t(implicit session: DBSession): Option[ArtifactType.T] =
  {
    artifactId.flatMap { aid => 
      val a = Artifact.syntax
      withSQL { 
        select(a.t)
          .from(Artifact as a)
          .where.eq(a.id, aid)
      }.map { _.get[ArtifactType.T](a.t) }.single.apply()
    }
  }

  def getSummary(implicit session: DBSession): Option[ArtifactSummary] =
    artifactId.map { Artifact.lookupSummary(_).get }
}

object InputArtifactRef
  extends SQLSyntaxSupport[ArtifactRef]
{
  def apply(rs: WrappedResultSet): ArtifactRef = autoConstruct(rs, (InputArtifactRef.syntax).resultName)
  override def columns = Schema.columns(table)
  override def tableName: String = "Input"
}

object OutputArtifactRef
  extends SQLSyntaxSupport[ArtifactRef]
{
  def apply(rs: WrappedResultSet): ArtifactRef = autoConstruct(rs, (OutputArtifactRef.syntax).resultName)
  override def columns = Schema.columns(table)
  override def tableName: String = "Output"
}