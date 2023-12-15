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
import java.net.URL
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import com.google.gson.JsonObject
import java.time.ZonedDateTime
import info.vizierdb.Vizier

case class PublishedArtifact(
  name: String,
  artifactId: Identifier,
  projectId: Identifier,
  properties: JsObject,
)
{
  def url = Vizier.urls.publishedArtifact(name)
  def artifact(implicit session: DBSession) = Artifact.get(target = artifactId, projectId = Some(projectId))
}

object PublishedArtifact
  extends SQLSyntaxSupport[PublishedArtifact]
{
  def apply(rs: WrappedResultSet): PublishedArtifact = autoConstruct(rs, (PublishedArtifact.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(name: String)(implicit session: DBSession): PublishedArtifact =
    getOption(name).get
  def getOption(name: String)(implicit session: DBSession): Option[PublishedArtifact] =
    withSQL { 
      val b = PublishedArtifact.syntax 
      select
        .from(PublishedArtifact as b)
        .where.eq(b.name, name)
    }.map { apply(_) }.single.apply()
  def make(
    artifact: Artifact, 
    name: Option[String] = None, 
    properties: JsObject = Json.obj(),
    overwrite: Boolean = false
  )(implicit session: DBSession): PublishedArtifact =
  {
    val actualName = name.getOrElse { s"${artifact.t}_${artifact.id}"}
    if(overwrite){
      withSQL {
        val a = PublishedArtifact.column
        deleteFrom(PublishedArtifact)
          .where.eq(a.name, actualName)
      }.update.apply()
    }
    withSQL {
      val a = PublishedArtifact.column
      insertInto(PublishedArtifact)
        .namedValues(
          a.name -> actualName,
          a.artifactId -> artifact.id,
          a.projectId -> artifact.projectId,
          a.properties -> properties
        )
    }.update.apply()
    PublishedArtifact(
      name = actualName,
      artifactId = artifact.id,
      projectId = artifact.projectId,
      properties = properties
    )
  }

  def nameFromURL(url: String): Option[String] = 
  {
    val base = 
      Vizier.urls.publishedArtifact("").toString
    if(url.startsWith(base)){
      Some(url.drop(base.length).split("/").head)
    } else { 
      return None
    }
  }
 
  def list(implicit session: DBSession): Seq[(String, Artifact)] =
  {
    val b = PublishedArtifact.syntax
    val a = Artifact.syntax
    withSQL { 
      select
        .from(PublishedArtifact as b)
          .join(Artifact as a)
        .where.eq(b.artifactId, a.id)
          .and.eq(b.projectId, a.projectId)
    }.map { rs => 
      (
        rs.string(b.resultName.name), 
        Artifact(rs)
      )
    }.list.apply()
  }
}