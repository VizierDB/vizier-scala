package info.vizierdb.catalog

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
  def summarize(name: String = null) = Artifact.summarize(id, projectId, t, created, mimeType, Option(name))

  def describe(
    name: String = null, 
    offset: Option[Long] = None, 
    limit: Option[Int] = None, 
    forceProfiler: Boolean = false
  ): JsObject = 
  {
    val base = summarize(name = name)
    val (extensions, links) = 
      t match { 
        case ArtifactType.DATASET => 
        {
          val data =  getDataset(
                        offset = offset, 
                        limit = limit, 
                        forceProfiler = forceProfiler, 
                        includeUncertainty = true
                      )
          (
            Json.obj(
              "rows"       -> ???,
              "rowCount"   -> ???,
              "offset"     -> ???,
              "properties" -> ???,
            ),
            HATEOAS(
              HATEOAS.PAGE_FIRST -> ???,
              HATEOAS.PAGE_PREV  -> ???,
              HATEOAS.PAGE_NEXT  -> ???,
              HATEOAS.PAGE_LAST  -> ???
            )
          )
        }
      }

    JsObject(base.value ++ extensions.value ++ 
      Map(
        HATEOAS.LINKS -> JsObject(
          base.value(HATEOAS.LINKS)
              .as[Map[String, JsValue]] 
              ++ links.as[Map[String, JsValue]]
        )
      )
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
        .where(sqls.toAndConditionOpt(
                projectId.map { sqls.eq(b.projectId, _) }
              ))
           .and.eq(b.id, target) 
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

  def summarize(id: Identifier, projectId: Identifier, t: ArtifactType.T, created: ZonedDateTime, mimeType: String, name: Option[String]): JsObject =
    Json.obj(
      "key" -> id,
      "id" -> id,
      "objType" -> mimeType, 
      "category" -> t.toString,
      "name" -> JsString(name.getOrElse(id.toString)),
      HATEOAS.LINKS -> HATEOAS((
        Seq(
          HATEOAS.SELF -> VizierAPI.urls.getArtifact(projectId.toString, id.toString),
        ) ++ (t match {
          case ArtifactType.DATASET => Seq(
            HATEOAS.DATASET_FETCH_ALL -> VizierAPI.urls.getDataset(projectId.toString, id.toString, limit = Some(-1)),
            HATEOAS.DATASET_DOWNLOAD  -> VizierAPI.urls.downloadDataset(projectId.toString, id.toString),
            HATEOAS.ANNOTATIONS_GET   -> VizierAPI.urls.getDatasetCaveats(projectId.toString, id.toString)
          )
          case _ => Seq()
        })
      ):_*)
    )
}