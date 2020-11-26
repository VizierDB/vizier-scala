package info.vizierdb.catalog

import java.io.File
import java.net.URL
import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import java.time.ZonedDateTime
import info.vizierdb.catalog.binders._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.Vizier
import org.mimirdb.api.request.DataContainer
import org.mimirdb.api.request.QueryTableRequest
import org.mimirdb.api.MimirAPI
import org.mimirdb.spark.SparkPrimitive
import info.vizierdb.filestore.Filestore

case class Artifact(
  id: Identifier,
  projectId: Identifier,
  t: ArtifactType.T,
  created: ZonedDateTime,
  mimeType: String,
  data: Array[Byte]
)
{
  def string = new String(data)
  def nameInBackend = Artifact.nameInBackend(t, id)
  def file: File = Filestore.get(projectId, id)
  def summarize(name: String = null) = 
    Artifact.summarize(id, projectId, t, created, mimeType, Option(name))
  def jsonData = Json.parse(data)


  def describe(
    name: String = null, 
    offset: Option[Long] = None, 
    limit: Option[Int] = None, 
    forceProfiler: Boolean = false
  ): JsObject = 
  {
    val (extensions, links): (Map[String,JsValue], Seq[(String,URL)]) = 
      t match { 
        case ArtifactType.DATASET => 
          {
            val actualLimit = 
              limit.getOrElse { VizierAPI.MAX_DOWNLOAD_ROW_LIMIT }

            val data =  getDataset(
                          offset = offset, 
                          limit = Some(actualLimit), 
                          forceProfiler = forceProfiler, 
                          includeUncertainty = true
                        )
            val rowCount: Long = 
                data.properties
                    .get("count")
                    .map { _.as[Long] }
                    .getOrElse { MimirAPI.catalog
                                         .get(nameInBackend)
                                         .count() }

            Artifact.translateDatasetContainerToVizierClassic(
              projectId = projectId,
              artifactId = id,
              data = data,
              offset = offset.getOrElse { 0 },
              limit = actualLimit,
              rowCount = rowCount
            )
            
          }
        case ArtifactType.BLOB | ArtifactType.FUNCTION | ArtifactType.FILE | ArtifactType.CHART => 
          (Map.empty, Seq.empty)
      }

    Artifact.summarize(
      artifactId = id, 
      projectId = projectId, 
      t = t, 
      created = created, 
      mimeType = mimeType, 
      name = Option(name),
      extraHateoas = links,
      extraFields = extensions,
    )
  }

  def getDataset(
    offset: Option[Long] = None, 
    limit: Option[Int] = None,
    forceProfiler: Boolean = false,
    includeUncertainty: Boolean = false
  ) : DataContainer = 
  {
    assert(t.equals(ArtifactType.DATASET))
    QueryTableRequest(
      table = nameInBackend,
      columns = None,
      offset = offset,
      limit = limit,
      profile = Some(forceProfiler),
      includeUncertainty = includeUncertainty
    ).handle
  }

  def deleteArtifact(implicit session: DBSession) =
  {
    withSQL { 
      val a = Artifact.syntax
      deleteFrom(Artifact)
        .where.eq(a.id, id)
    }.update.apply()

    t match {
      case ArtifactType.FILE => 
        Filestore.remove(projectId, id)
      case ArtifactType.DATASET =>
        // MimirAPI.catalog.drop()
      case _ => ()
    }
  }
}

case class ArtifactSummary(
  id: Identifier,
  projectId: Identifier, 
  t: ArtifactType.T,
  created: ZonedDateTime,
  mimeType: String,
)
{
  def nameInBackend = Artifact.nameInBackend(t, id)
  def summarize(name: String = null) = Artifact.summarize(id, projectId, t, created, mimeType, Option(name))
  def file = Filestore.get(projectId, id)
}

object Artifact
  extends SQLSyntaxSupport[Artifact]
{
  def apply(rs: WrappedResultSet): Artifact = autoConstruct(rs, (Artifact.syntax).resultName)
  override def columns = Schema.columns(table)

  def make(projectId: Identifier, t: ArtifactType.T, mimeType: String, data: Array[Byte])(implicit session: DBSession): Artifact =
  {
    val artifactId = withSQL {
      val a = Artifact.column
      insertInto(Artifact)
        .namedValues(
          a.projectId -> projectId,
          a.t -> t,
          a.mimeType -> mimeType,
          a.created -> ZonedDateTime.now(),
          a.data -> data
        )
    }.updateAndReturnGeneratedKey.apply()
    Artifact.get(artifactId)
  }

  def get(target: Identifier, projectId: Option[Identifier] = None)(implicit session:DBSession): Artifact = lookup(target, projectId).get
  def lookup(target: Identifier, projectId: Option[Identifier] = None)(implicit session:DBSession): Option[Artifact] = 
    withSQL { 
      val b = Artifact.syntax 
      select
        .from(Artifact as b)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(b.id, target)),
            projectId.map { sqls.eq(b.projectId, _) }
          )
        )
    }.map { apply(_) }.single.apply()

  def nameInBackend(t: ArtifactType.T, artifactId: Identifier): String =
    s"${t}_${artifactId}"

  def lookupSummary(target: Identifier)(implicit session:DBSession): Option[ArtifactSummary] =
  {
    withSQL {
      val a = Artifact.syntax
      select(a.projectId, a.t, a.created, a.mimeType)
        .from(Artifact as a)
        .where.eq(a.id, target)
    }.map { rs => ArtifactSummary(target, rs.long(1), ArtifactType(rs.int(2)), rs.dateTime(3), rs.string(4)) }
     .single.apply()
  }

  def summarize(
    artifactId: Identifier, 
    projectId: Identifier, 
    t: ArtifactType.T, 
    created: ZonedDateTime, 
    mimeType: String, 
    name: Option[String],
    extraHateoas: Seq[(String, URL)] = Seq.empty,
    extraFields: Map[String, JsValue] = Map.empty
  ): JsObject =
    JsObject(Map(
      "key" -> JsNumber(artifactId),
      "id" -> JsNumber(artifactId),
      "objType" -> JsString(mimeType), 
      "category" -> JsString(t.toString),
      "name" -> JsString(name.getOrElse(artifactId.toString)),
      HATEOAS.LINKS -> HATEOAS((
        Seq(
          HATEOAS.SELF -> (t match {
            case ArtifactType.DATASET => 
              VizierAPI.urls.getDataset(projectId, artifactId)
            case ArtifactType.CHART => 
              VizierAPI.urls.getChartView(projectId, 0, 0, 0, artifactId)
            case _ => 
              VizierAPI.urls.getArtifact(projectId, artifactId)
          })
        ) ++ (t match {
          case ArtifactType.DATASET => Seq(
            HATEOAS.DATASET_FETCH_ALL -> VizierAPI.urls.getDataset(projectId, artifactId, limit = Some(-1)),
            HATEOAS.DATASET_DOWNLOAD  -> VizierAPI.urls.downloadDataset(projectId, artifactId),
            HATEOAS.ANNOTATIONS_GET   -> VizierAPI.urls.getDatasetCaveats(projectId, artifactId)
          )
          case _ => Seq()
        }) ++ extraHateoas
      ):_*),
    ) ++ extraFields)

  /**
   * Translate a Mimir DataContainer to something the frontend UI wants to see
   *
   * The main use of this is to produce Dataset Artifacts for consumption by the
   * frontend via artifact.describe, but we occasionally need to create 
   * similar structures elsewhere (e.g., when we want to cache a DataContainer)
   */
  def translateDatasetContainerToVizierClassic(
    projectId: Identifier,
    artifactId: Identifier, 
    data: DataContainer,
    offset: Long,
    limit: Int,
    rowCount: Long
  ): (Map[String,JsValue],Seq[(String, URL)]) = 
  {
    (
      Map(
        "columns"    -> JsArray(data.schema.zipWithIndex.map { case (field, idx) => 
                          Json.obj(
                            "id" -> idx,
                            "name" -> field.name,
                            "type" -> DATATYPE.fromSpark(field.dataType).toString
                          )
                        }),
        "rows"       -> JsArray(
          data.data
              .zip(data.prov)
              .zip(data.rowTaint.zip(data.colTaint))
              .map { case ((row, rowid), (rowCaveatted, attrCaveats)) => 
                Json.obj(
                  "id" -> rowid,
                  "values" -> JsArray(
                    data.schema.zip(row)
                        .map { case (col, v) => SparkPrimitive.encode(v, col.dataType) }
                  ),
                  "rowAnnotationFlags" -> JsArray(attrCaveats.map { c => JsBoolean(!c) }),
                  "rowIsAnnotated"     -> rowCaveatted
                )
              }
        ),
        "rowCount"   -> JsNumber(rowCount),
        "offset"     -> JsNumber(offset),
        "properties" -> JsObject(data.properties),
      ),
      Seq(
        HATEOAS.PAGE_FIRST -> (if(offset <= 0){ null }
                               else { VizierAPI.urls.getDataset(
                                        projectId, artifactId, 
                                        offset = Some(0), 
                                        limit = Some(limit)) }),
        HATEOAS.PAGE_PREV  -> (if(offset <= 0){ null }
                               else { VizierAPI.urls.getDataset(
                                        projectId, artifactId, 
                                        offset = Some(if(offset - limit > 0) { 
                                                        (offset - limit) 
                                                      } else { 0l }), 
                                        limit = Some(limit)) }),
        HATEOAS.PAGE_NEXT  -> (if(offset + limit >= rowCount){ null }
                               else { VizierAPI.urls.getDataset(
                                        projectId, artifactId, 
                                        offset = Some(offset + limit), 
                                        limit = Some(limit)) }),
        HATEOAS.PAGE_LAST  -> (if(offset + limit >= rowCount){ null }
                               else { VizierAPI.urls.getDataset(
                                        projectId, artifactId, 
                                        offset = Some(rowCount - (rowCount % limit)), 
                                        limit = Some(limit)) }),
      )
    )
  }
}