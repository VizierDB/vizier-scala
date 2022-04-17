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

import info.vizierdb.types._


sealed trait PredictedArtifactVersion
{
  /**
   * Return true if an artifact with this version prevents running the cell.
   */
  def isRunnable: Boolean
  /**
   * Return true if an artifact with this version requires re-running the
   * cell given the prior execution using an artifact with the provided id.
   */
  def priorVersionRequiresReexecution(priorId: Identifier): Boolean
}
sealed trait OpenWorldPredictedArtifactVersion extends PredictedArtifactVersion

case object UnknownArtifactVersion extends OpenWorldPredictedArtifactVersion
{
  /**
   * If we don't have a version yet, we can not re-run the cell
   */
  val isRunnable: Boolean = false
  /**
   * If we don't have a version yet, we don't know whether the cell needs
   * to be re run.  Nominally this means a that we can't provide an answer, but
   * we're going to default to returning false if we don't know.
   */
  def priorVersionRequiresReexecution(priorId: Identifier): Boolean = false

  override def toString: String = "[??]"
}
case object ChangedArtifactVersion extends PredictedArtifactVersion
{
  /** 
   * If the version is different, but we don't have the new version, then 
   * we can't run anything yet
   */
  val isRunnable: Boolean = false
  /**
   * If the version is different, then we do need to re-run the cell.
   */
  def priorVersionRequiresReexecution(priorId: Identifier): Boolean = true

  override def toString: String = "[fresh id]"
}
case object ArtifactDoesNotExist extends OpenWorldPredictedArtifactVersion
{
  /**
   * If the artifact will be deleted, it's not in the scope and we can rerun it.
   */
  val isRunnable: Boolean = true
  /**
   * If the artifact will be deleted, then we need to re-run the cell.
   * (It'll probably explode)
   */
  def priorVersionRequiresReexecution(priorId: Identifier): Boolean = true

  override def toString: String = "[no artifact]"
}
case class ExactArtifactVersion(artifactId: Identifier) extends PredictedArtifactVersion
{
  /**
   * If we have an exact version, we're good to go.
   */
  val isRunnable: Boolean = true
  /**
   * If we have an exact version, then re-execution is dependent entirely
   * on whether the version we have is the same as the one that was read.
   */
  def priorVersionRequiresReexecution(priorId: Identifier): Boolean = 
    !priorId.equals(artifactId)

  override def toString: String = f"[$artifactId]"
}
