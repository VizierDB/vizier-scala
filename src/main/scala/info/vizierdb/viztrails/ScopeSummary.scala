package info.vizierdb.viztrails

import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.catalog._
import com.typesafe.scalalogging.LazyLogging

case class ScopeSummary(scope: Map[String, Identifier])
{
  def withUpdates(cell: Cell)(implicit session: DBSession): ScopeSummary = 
    withUpdates(cell.outputs.map { x => x.userFacingName -> x.artifactId }.toMap)
  def withUpdates(updates: Map[String, Option[Identifier]]): ScopeSummary = 
  {
    val (deleteRefs, insertRefs) = updates.partition { _._2.isEmpty }
    val deletions = deleteRefs.map { _._1 }.toSet
    val insertions = insertRefs.toMap.mapValues { _.get }
    ScopeSummary(scope.filterNot { case (k, v) => deletions(k) } ++ insertions)
  }

  def apply(artifact: String): Option[Identifier] = 
    scope.get(artifact)
  def contains(artifact: String): Boolean = 
    scope.contains(artifact)

  def namedArtifactSummaries(implicit session: DBSession): Map[String, ArtifactSummary] =
  {
    val summaries = Artifact.lookupSummaries(scope.values.toSeq)
                            .map { a => a.id -> a }
                            .toMap
    scope.mapValues { summaries(_) }
  }

}

object ScopeSummary
  extends Object
  with LazyLogging
{

  def ofRefs(scope: Seq[ArtifactRef]): ScopeSummary = 
    ScopeSummary(
      scope.collect { case ArtifactRef(_, Some(artifactId), userFacingName) => 
                        userFacingName -> artifactId }
           .toMap
    )

  def empty: ScopeSummary = ScopeSummary(Map[String, Identifier]())

  def apply(cell: Cell)(implicit session: DBSession): ScopeSummary =
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    ScopeSummary(
      withSQL {
        select
          .from(Cell as c)
          .join(OutputArtifactRef as o)
          .where.eq(c.resultId, o.resultId)
            .and.eq(c.workflowId, cell.workflowId)
            .and.lt(c.position, cell.position)
          .orderBy(c.position.desc)
      }.map { OutputArtifactRef(_) }
       .list.apply()
       // identifier = None means that the dataset was deleted
       .foldLeft(Map[String, ArtifactRef]()) { 
        (scope:Map[String, ArtifactRef], output:ArtifactRef) =>
          logger.trace(s"Get Scope: Adding $output")
          // Thanks to the orderBy above, the first version of each identifier
          // that we encounter should be the right one.
          if(scope contains output.userFacingName) { scope }
          else { scope ++ Map(output.userFacingName -> output) }
       }
       .filterNot { _._2.artifactId.isEmpty }
       .mapValues { _.artifactId.get }
    )
  }
}
