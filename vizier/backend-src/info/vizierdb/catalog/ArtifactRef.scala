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
      }.map { _.get[ArtifactType.T](1) }.single.apply()
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

