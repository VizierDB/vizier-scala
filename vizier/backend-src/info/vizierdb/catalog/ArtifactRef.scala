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

  def tuple: Option[(String, Identifier)] =
    artifactId.map { (userFacingName -> _) }
}

object InputArtifactRef
  extends SQLSyntaxSupport[ArtifactRef]
{
  def apply(rs: WrappedResultSet): ArtifactRef = autoConstruct(rs, (InputArtifactRef.syntax).resultName)
  override def columns = Schema.columns(table)
  override def tableName: String = "Input"

  def inputArtifactIdsForWorkflow(
    workflowId: Identifier
  )(implicit session: DBSession): Map[Cell.Position, Map[String, Identifier]] =
  {
    val c = Cell.syntax
    val i = InputArtifactRef.syntax
    withSQL {
      select
        .from(Cell as c)
        .join(InputArtifactRef as i)
        .where.eq(c.workflowId, workflowId)
          .and.eq(i.resultId, c.resultId)
    }.map { rs => (rs.int(c.resultName.position), InputArtifactRef(rs)) }
     .list.apply()
     .groupBy { _._1 }
     .mapValues { _.flatMap { _._2.tuple }.toMap }
  }

}

object OutputArtifactRef
  extends SQLSyntaxSupport[ArtifactRef]
{
  def apply(rs: WrappedResultSet): ArtifactRef = autoConstruct(rs, (OutputArtifactRef.syntax).resultName)
  override def columns = Schema.columns(table)
  override def tableName: String = "Output"

  def outputArtifactRefsForWorkflow(
    workflowId: Identifier
  )(implicit session: DBSession): Map[Cell.Position, Seq[ArtifactRef]] =
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    withSQL {
      select
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .where.eq(c.workflowId, workflowId)
          .and.eq(o.resultId, c.resultId)
    }.map { rs => (rs.int(c.resultName.position), OutputArtifactRef(rs)) }
     .list.apply()
     .groupBy { _._1 }
     .mapValues { _.map { _._2 } }
  }

  def outputArtifactsForWorkflow(
    workflowId: Identifier
  )(implicit session: DBSession): Map[Cell.Position, Map[String, Artifact]] =
  {
    val c = Cell.syntax
    val s = Artifact.syntax
    val o = OutputArtifactRef.syntax

    withSQL {
      select
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .join(Artifact as s)
        .where.eq(c.workflowId, workflowId)
          .and.eq(o.resultId, c.resultId)
          .and.eq(o.artifactId, s.id)
    }.map { rs => (
        rs.int(c.resultName.position), (
          rs.string(o.resultName.userFacingName),
          Artifact(rs)
        )
      ) }
     .list.apply()
     .groupBy { _._1 }
     .mapValues { _.map { _._2 }.toMap }
  }
}

