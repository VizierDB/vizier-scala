package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import org.mimirdb.api.request.DataContainer
import java.time.ZonedDateTime


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
  def describe(implicit session: DBSession): JsValue =
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
          val (fields, links) =
            Artifact.translateDatasetContainerToVizierClassic(
              projectId = projectId,
              artifactId = artifactId,
              data = cache,
              offset = offset,
              limit = cache.data.size,
              rowCount = rowCount
            )
          Artifact.summarize(
            artifactId = artifactId, 
            projectId = projectId, 
            t = ArtifactType.DATASET, 
            created = created, 
            mimeType = MIME.DATASET_VIEW, 
            name = name,
            extraHateoas = links,
            extraFields = fields
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

  def describe(implicit session: DBSession): JsObject = 
    Json.obj(
      "type" -> mimeType,
      "value" -> (mimeType match {
        case MIME.DATASET_VIEW => Json.parse(data).as[DatasetMessage].describe
        case _ => JsString(new String(data))
      })
    )
}
object Message 
  extends SQLSyntaxSupport[Message]
{
  def apply(rs: WrappedResultSet): Message = autoConstruct(rs, (Message.syntax).resultName)
  override def columns = Schema.columns(table)
}