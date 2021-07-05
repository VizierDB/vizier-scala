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
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import org.mimirdb.api.request.DataContainer
import java.time.ZonedDateTime
import info.vizierdb.serializers._
import info.vizierdb.serialized


case class DatasetMessage(
  name: Option[String],
  artifactId: Identifier,
  projectId: Identifier,
  offset: Long,
  dataCache: Option[DataContainer],
  rowCount: Long,
  created: ZonedDateTime
)
{
  def describe(implicit session: DBSession): serialized.ArtifactDescription =
  {
    // how we proceed depends on whether we have a cached data container
    dataCache match { 
      // without a cache, just run through the normal artifact process
      case None => Artifact.get(artifactId, Some(projectId))
                           .describe( 
                              name = name.getOrElse { "Untitled Dataset" },
                              offset = Some(offset),
                            )
      // otoh, if we have a cache, we need to work around Artifact
      case Some(cache) => 
        {
          val base = 
            Artifact.summarize(
              artifactId = artifactId, 
              projectId = projectId, 
              t = ArtifactType.DATASET, 
              created = created, 
              mimeType = MIME.DATASET_VIEW, 
              name = name
            )
          Artifact.translateDatasetContainerToVizierClassic(
            projectId = projectId,
            artifactId = artifactId,
            data = cache,
            offset = offset,
            limit = cache.data.size,
            rowCount = rowCount,
            base = base
          )
        }

    }
  }
}
object DatasetMessage
{
  implicit val format: Format[DatasetMessage] = Json.format
}


case class Message(
  val resultId: Identifier,
  val mimeType: String,
  val data: Array[Byte],
  val stream: StreamType.T
)
{
  def dataString: String = new String(data)

  def describe(implicit session: DBSession): serialized.MessageDescription = 
    try { 
      val t = MessageType.withName(mimeType)
      serialized.MessageDescription(
        `type` = t,
        value = (t match {
          case MessageType.DATASET => Json.toJson(Json.parse(data).as[DatasetMessage].describe)
          case MessageType.CHART => Json.parse(data)
          case MessageType.JAVASCRIPT => Json.parse(data)
          case MessageType.HTML => JsString(new String(data))
          case MessageType.TEXT => JsString(new String(data))
        })
      )
    } catch {
      case e: Throwable => 
        serialized.MessageDescription(
          `type` = MessageType.TEXT,
          value  = JsString(s"Error retrieving message: $e")
        )
    }
}
object Message 
  extends SQLSyntaxSupport[Message]
{
  def apply(rs: WrappedResultSet): Message = autoConstruct(rs, (Message.syntax).resultName)
  override def columns = Schema.columns(table)
}

