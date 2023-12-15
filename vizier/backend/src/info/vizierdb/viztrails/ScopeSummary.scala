/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
    ScopeSummary(
      scope
        ++ (outputs.mapValues { 
          case None => ArtifactDoesNotExist
          case Some(artifactId) => ExactArtifactVersion(artifactId)
        }.map { case (name, version) => name.toLowerCase -> version }
         .toMap),
      openWorldPrediction
    )
  }
  def copyWithPredictionForStaleCell(prediction: ProvenancePrediction) =
    copyWithPrediction(prediction, ChangedArtifactVersion)
  def copyWithPredictionForWaitingCell(prediction: ProvenancePrediction) =
    copyWithPrediction(prediction, UnknownArtifactVersion)
  def copyWithPrediction(
    prediction: ProvenancePrediction, 
    writeVersion: PredictedArtifactVersion
  ) =
  {
    val newPredictions = (
         prediction.deletes.map { _.toLowerCase -> ArtifactDoesNotExist }.toMap
      ++ prediction.writes.map { _.toLowerCase -> writeVersion }.toMap
    )
    if(prediction.openWorldWrites){
      // If we don't know what the cell is going to write, then all artifacts in
      // the existing scope are fair game.  Drop them and replace with a generic
      // "unknown" version.
      ScopeSummary(
        newPredictions,
        UnknownArtifactVersion
      )
    } else {
      // If we're in a closed world, then only update the artifacts in the
      // scope with the proposed writes and deletes
      ScopeSummary(
        scope ++ newPredictions,
        openWorldPrediction
      )
    }
  }
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
          copyWithAnOpenWorld

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
      module.command.map { _.predictProvenance(module.arguments, module.properties) }
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

  def allArtifacts(implicit session: DBSession): Map[String, Artifact] = 
    artifactsFor(scope.keys)

  def artifactsFor
    (artifacts: Iterable[String])
    (implicit session: DBSession): 
      Map[String, Artifact] =
  {
    val identifiers: Map[String, Identifier] =
      artifacts.map { name => name.toLowerCase -> apply(name.toLowerCase) }
               .collect { case (name, ExactArtifactVersion(id)) => name -> id }
               .toMap
    val summaries = Artifact.getAll(identifiers.values.toSeq)
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
