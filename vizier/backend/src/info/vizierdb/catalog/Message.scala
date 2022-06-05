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
import info.vizierdb.spark.caveats.DataContainer
import java.time.ZonedDateTime
import info.vizierdb.serializers._
import info.vizierdb.serialized
import com.typesafe.scalalogging.LazyLogging


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
  def describe(implicit session: DBSession): () => serialized.ArtifactDescription =
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
            serialized.StandardArtifact(
              key       = artifactId,
              id        = artifactId,
              projectId = projectId,
              objType   = MIME.DATASET_VIEW,
              category  = ArtifactType.DATASET,
              name      = name.getOrElse { s"Unnamed Dataset $artifactId" },
            )

          () => 
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

case class JavascriptMessage(
  // javascript to run once the HTML below loads
  code: String,
  // a bit of HTML to display in the output
  html: String,
  // javascript dependency URLs
  js_deps: Seq[String],
  // css dependency URLs
  css_deps: Seq[String]
)
object JavascriptMessage
{
  implicit val format: Format[JavascriptMessage] = Json.format
}


/** Note: mimeType should actually be named messageType **/
case class Message(
  val resultId: Identifier,
  val mimeType: String,
  val data: Array[Byte],
  val stream: StreamType.T
)
  extends LazyLogging
{
  def dataString: String = new String(data)
  def dataJson: JsValue = Json.parse(data)

  def describe(implicit session: DBSession): () => serialized.MessageDescription = 
    try { 
      val t = MessageType.decode(mimeType)
      val value = 
        (t match {
          case MessageType.DATASET => 
            val base: () => serialized.ArtifactDescription = 
              Json.parse(data).as[DatasetMessage].describe

            { () => Json.toJson(base()) }
          case MessageType.CHART => { () => dataJson }
          case MessageType.JAVASCRIPT => { () => dataJson }
          case MessageType.HTML => { () => JsString(new String(data)) }
          case MessageType.TEXT => { () => JsString(new String(data)) }
          case MessageType.MARKDOWN => { () => JsString(new String(data)) }
          case MessageType.VEGALITE => { () => dataJson }
          case MessageType.PNG_IMAGE => { () => JsString(new String(data)) }
        })

      () => 
        serialized.MessageDescription(
          `type` = t,
          value = value()
        )
    } catch {
      case e: Throwable => 
        logger.error(s"Error retrieving message: ${e.getMessage}\n${e.getStackTraceString}")
        e.printStackTrace()
        () => 
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

  def messagesForWorkflow(
    workflowId: Identifier
  )(implicit session: DBSession): Map[Cell.Position, Seq[Message]] =
  {
    val m = Message.syntax
    val c = Cell.syntax
    withSQL {
      select
        .from(Cell as c)
        .join(Message as m)
        .where.eq(c.workflowId, workflowId)
          .and.eq(m.resultId, c.resultId)
    }.map { rs => (rs.int(c.resultName.position), Message(rs)) }
     .list.apply()
     .groupBy { _._1 }
     .mapValues { _.map { _._2 } }    
  }

}

