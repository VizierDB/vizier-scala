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

case class ProvenancePrediction(
  val reads: Set[String] = Set.empty,
  val deletes: Set[String] = Set.empty,
  val writes: Set[String] = Set.empty,
  val openWorldReads: Boolean = true,
  val openWorldWrites: Boolean = true,
)
{
  def definitelyReads(i: String*) = 
    copy( reads = i.toSet )
  def definitelyWrites(i: String*) = 
    copy( writes = i.toSet )
  def definitelyDeletes(i: String*) = 
    copy( deletes = i.toSet )
  def andNothingElse = 
    copy( openWorldReads = false, openWorldWrites = false )

}

object ProvenancePrediction
{
  def default = ProvenancePrediction()

  def definitelyReads(i: String*) = 
    ProvenancePrediction( reads = i.toSet )
  def definitelyWrites(i: String*) = 
    ProvenancePrediction( writes = i.toSet )
  def definitelyDeletes(i: String*) = 
    ProvenancePrediction( deletes = i.toSet )
  def empty = 
    ProvenancePrediction( openWorldReads = false, openWorldWrites = false )
}