package info.vizierdb.viztrails

import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.catalog._
import com.typesafe.scalalogging.LazyLogging

case class ScopeSummary(
  scope: Map[String, PredictedArtifactVersion],
  openWorldPrediction: OpenWorldPredictedArtifactVersion
)
{
  def copyWithOutputs(outputs: Map[String, Option[Identifier]]): ScopeSummary =
  {
    val (deleteRefs, insertRefs) = outputs.toSeq.partition { _._2.isEmpty }
    val deletions = deleteRefs.map { _._1.toLowerCase }.toSet
    val insertions = insertRefs.map { case (userFacingName, artifactId) => 
                        userFacingName.toLowerCase -> ExactArtifactVersion(artifactId.get)
                     }.toMap
    ScopeSummary(
      scope.filterNot { case (k, v) => deletions(k) } ++ insertions,
      openWorldPrediction
    )
  }
  def copyWithPredictionForStaleCell(prediction: ProvenancePrediction) =
    copyWithPrediction(prediction, ChanrgedArtifactVersion)
  def copyWithPredictionForWaitingCell(prediction: ProvenancePrediction) =
    copyWithPrediction(prediction, UnknownArtifactVersion)
  def copyWithPrediction(
    prediction: ProvenancePrediction, 
    writeVersion: PredictedArtifactVersion
  ) =
    ScopeSummary(
      scope
        ++ prediction.deletes.map { _.toLowerCase -> ArtifactDoesNotExist }.toMap
        ++ prediction.writes.map { _.toLowerCase -> writeVersion }.toMap,
      if(prediction.openWorldWrites){
        UnknownArtifactVersion
      } else {
        openWorldPrediction
      }
    )
  def copyWithAnOpenWorld =
    ScopeSummary(
      Map.empty,
      UnknownArtifactVersion
    )

  def copyWithUpdatesFromCellMetadata(
    state: ExecutionState.T,
    outputs: => Seq[ArtifactRef],
    predictedProvenance: => ProvenancePrediction
  ) = 
    state match {
      case ExecutionState.FROZEN => this

      case ExecutionState.DONE => 
        copyWithOutputs(
          outputs = outputs
                        .map { ref => ref.userFacingName -> ref.artifactId }
                        .toMap
        )

        case ExecutionState.ERROR | ExecutionState.CANCELLED => 
          assert(false, "We should have returned from an ERROR or CANCELLED state before now"); null

        case ExecutionState.WAITING  =>
          copyWithPredictionForWaitingCell(predictedProvenance)

        case ExecutionState.STALE | ExecutionState.RUNNING =>
          copyWithPredictionForStaleCell(predictedProvenance)
      }
  def copyWithUpdatesForCell(cell: Cell)(implicit session: DBSession) = 
  {
    val module = cell.module
    copyWithUpdatesFromCellMetadata(
      cell.state,
      cell.outputs,
      module.command.map { _.predictProvenance(module.arguments) }
                    .getOrElse { ProvenancePrediction.default }
    )
  }

  def apply(artifact: String): PredictedArtifactVersion = 
    scope.getOrElse(artifact.toLowerCase, openWorldPrediction)

  def isRunnableForKnownInputs(artifacts: Iterable[String]): Boolean =
    artifacts.map { apply(_) }
             .forall { _.isRunnable } 

  def isRunnableForUnknownInputs: Boolean =
    (scope.values ++ Seq(openWorldPrediction))
             .forall { _.isRunnable } 

  def allArtifactSummaries(implicit session: DBSession): Map[String, ArtifactSummary] = 
    artifactSummariesFor(scope.keys)

  def artifactSummariesFor
    (artifacts: Iterable[String])
    (implicit session: DBSession): 
      Map[String, ArtifactSummary] =
  {
    val identifiers: Map[String, Identifier] =
      artifacts.map { name => name.toLowerCase -> apply(name.toLowerCase) }
               .collect { case (name, ExactArtifactVersion(id)) => name -> id }
               .toMap
    val summaries = Artifact.lookupSummaries(identifiers.values.toSeq)
                            .map { summary => summary.id -> summary }
                            .toMap 
    return identifiers.mapValues { summaries(_) }
  }

  override def toString: String =
  {
    val scopeText =
      ( scope.mapValues { _.toString }.toSeq ++ (
          openWorldPrediction match { 
            case ArtifactDoesNotExist => None
            case x => Some( ("[*]" -> x.toString) )
          }
        )
      ).map { case (name, id) => s"$name -> $id" }
    if(scopeText.isEmpty){ "{ [empty scope] }" }
    else { s"{ ${scopeText.mkString(", ")} }" }
  }
}

object ScopeSummary
{
  def empty = ScopeSummary(Map.empty, ArtifactDoesNotExist)

  def apply(cell: Cell)(implicit session: DBSession): ScopeSummary =
  {
    apply(cell.predecessors :+ cell)
  }

  def apply(cells: Seq[Cell])(implicit session: DBSession): ScopeSummary =
  {
    cells.foldLeft(empty) { _.copyWithUpdatesForCell(_) }
  }

  def withIds(artifacts: Map[String, Identifier]) = 
    empty.copyWithOutputs(artifacts.mapValues { Some(_) })
}