package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import java.time.ZonedDateTime
import info.vizierdb.catalog.binders._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.Vizier

case class Artifact(
  id: Identifier,
  t: ArtifactType.T,
  created: ZonedDateTime,
  mimeType: String,
  data: Array[Byte]
)
{
  def string = new String(data)
  def nameInBackend = Artifact.nameInBackend(t, id)
  def describe = Artifact.describe(id, t, created, mimeType)
}

case class ArtifactSummary(
  id: Identifier,
  t: ArtifactType.T,
  created: ZonedDateTime,
  mimeType: String,
)
{
  def nameInBackend = Artifact.nameInBackend(t, id)
  def describe = Artifact.describe(id, t, created, mimeType)
}

object Artifact
  extends SQLSyntaxSupport[Artifact]
{
  def apply(rs: WrappedResultSet): Artifact = autoConstruct(rs, (Artifact.syntax).resultName)
  override def columns = Schema.columns(table)

  def make(t: ArtifactType.T, mimeType: String, data: Array[Byte])(implicit session: DBSession): Artifact =
  {
    val artifactId = withSQL {
      val a = Artifact.column
      insertInto(Artifact)
        .namedValues(
          a.t -> t,
          a.mimeType -> mimeType,
          a.created -> ZonedDateTime.now(),
          a.data -> data
        )
    }.updateAndReturnGeneratedKey.apply()
    Artifact.get(artifactId)
  }

  def get(target: Identifier)(implicit session:DBSession): Artifact = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Artifact] = 
    withSQL { 
      val b = Artifact.syntax 
      select
        .from(Artifact as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()

  def nameInBackend(t: ArtifactType.T, artifactId: Identifier): String =
    s"${t}_${artifactId}"

  def lookupSummary(target: Identifier)(implicit session:DBSession): Option[ArtifactSummary] =
  {
    withSQL {
      val b = Artifact.syntax
      select(b.t, b.created, b.mimeType)
        .from(Artifact as b)
        .where.eq(b.id, target)
    }.map { rs => ArtifactSummary(target, ArtifactType(rs.int(1)), rs.dateTime(2), rs.string(3)) }
     .single.apply()
  }

  def describe(id: Identifier, t: ArtifactType.T, created: ZonedDateTime, mimeType: String): JsObject =
    Json.obj(
      "key" -> id,
      "id" -> id,
      "objType" -> mimeType, 
      "category" -> t.toString,
      "name" -> id,
      HATEOAS.LINKS -> HATEOAS((
        Seq(
          HATEOAS.SELF -> VizierAPI.urls.getArtifact(id.toString()),
        ) ++ (t match {
          case ArtifactType.DATASET => Seq(
            HATEOAS.DATASET_FETCH_ALL -> VizierAPI.urls.getDataset(id.toString, limit = Some(-1)),
            HATEOAS.DATASET_DOWNLOAD  -> VizierAPI.urls.downloadDataset(id.toString),
            HATEOAS.ANNOTATIONS_GET   -> VizierAPI.urls.getDatasetCaveats(id.toString)
          )
          case _ => Seq()
        })
      ):_*)
    )
}