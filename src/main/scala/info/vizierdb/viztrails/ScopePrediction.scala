package info.vizierdb.viztrails

import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.catalog._
import com.typesafe.scalalogging.LazyLogging

case class ProvenancePrediction(
  definitelyReads: Set[String],
  definitelyDeletes: Set[String],
  definitelyWrites: Set[String],
  readsFromOpenWorld: Boolean = false,
  writesToOpenWorld: Boolean = false,
)

sealed trait PredictedArtifactVersion
sealed trait OpenWorldPredictedArtifactVersion extends PredictedArtifactVersion

case object UnknownVersion extends OpenWorldPredictedArtifactVersion
case object VersionUnchangedSinceLastRun extends OpenWorldPredictedArtifactVersion
case object VersionChangedSinceLastRun extends PredictedArtifactVersion
case object ArtifactWillBeDeleted extends PredictedArtifactVersion

case class ScopePrediction(
  scope: Map[String, PredictedArtifactVersion],
  openWorldPrediction: OpenWorldPredictedArtifactVersion
)
{
  def updateWithOutputs(outputs: Map[String, Option[Identifier]]): ScopePrediction =
  {
    val (deleteRefs, insertRefs) = outputs.toSeq.partition { _._2.isEmpty }
    val deletions = deleteRefs.map { _._1 }.toSet
    val insertions = insertRefs.map { case (userFacingName, artifactId) => 
                        userFacingName -> VersionUnchangedSinceLastRun
                     }.toMap
    ScopePrediction(
      scope.filterNot { case (k, v) => deletions(k) } ++ insertions,
      openWorldPrediction
    )
  }
  def updateWithStaleCellPrediction(prediction: ProvenancePrediction)
  {
    ScopePrediction(
      scope
        ++ prediction.definitelyDeletes.map { _ -> ArtifactWillBeDeleted }.toMap
        ++ prediction.definitelyWrites.map { _ -> VersionChangedSinceLastRun }.toMap,
      if(prediction.writesToOpenWorld){
        UnknownVersion
      } else {
        openWorldPrediction
      }
    )
  }
  def updateWithOpenWorld
  {
    ScopePrediction(
      Map.empty,
      UnknownVersion
    )
  }
}

object ScopePrediction
{
  def empty = ScopePrediction(Map.empty, VersionUnchangedSinceLastRun)
}