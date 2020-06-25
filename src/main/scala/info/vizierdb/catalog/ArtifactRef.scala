package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.types._
import java.time.ZonedDateTime

case class ArtifactRef(
  val resultId: Identifier,
  val artifactId: Identifier,
  val userFacingName: String
){ 
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