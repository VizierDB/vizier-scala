package info.vizierdb.ui.components

import scala.scalajs.js
import info.vizierdb.ui.network.{ ArtifactSummary, DatasetColumn, DatasetSummary }
import info.vizierdb.types.ArtifactType

sealed trait ArtifactMetadata

object ArtifactMetadata
{
  def apply(summary: ArtifactSummary): Option[ArtifactMetadata] =
  {
    if(summary.category.isEmpty){ return None }
    else { Some(
      ArtifactType.withName(summary.category.get) match {
        case ArtifactType.DATASET => 
          DatasetMetadata(summary.asInstanceOf[DatasetSummary].columns.toSeq)
        case _ => return None
      }
    )}
  }
}

case class DatasetMetadata(columns: Seq[DatasetColumn]) extends ArtifactMetadata

class Artifact(
  val name: String, 
  val isDeletion: Boolean,
  val id: String, 
  val t: ArtifactType.T, 
  val mimeType: String,
  val metadata: Option[ArtifactMetadata]
)
{

  def this(summary: ArtifactSummary)
  {
    this(
      name = summary.name, 
      isDeletion = summary.id.isEmpty,
      id = summary.id.map { _.toString }.orNull,
      t = summary.category.map { ArtifactType.withName(_) }.orNull,
      mimeType = summary.objType.orNull,
      ArtifactMetadata(summary)
    )
    println("Done")
  }

}