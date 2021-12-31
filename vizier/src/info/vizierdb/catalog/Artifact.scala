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

import java.io.File
import java.net.URL
import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import java.time.ZonedDateTime
import info.vizierdb.artifacts.Dataset
import info.vizierdb.catalog.binders._
import info.vizierdb.shared.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.Vizier
import info.vizierdb.spark.SparkPrimitive
import info.vizierdb.filestore.Filestore
import info.vizierdb.util.StupidReactJsonMap
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.spark.caveats.{ QueryWithCaveats, DataContainer }
import info.vizierdb.spark.SparkSchema.fieldFormat

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
  def absoluteFile: File = Filestore.getAbsolute(projectId, id)
  def relativeFile: File = Filestore.getRelative(projectId, id)
  def file = absoluteFile
  def parameter = json.as[serialized.ParameterArtifact]
  def json = Json.parse(string)
  def datasetDescriptor = json.as[Dataset]
  def dataframe(implicit session:DBSession) = 
    datasetDescriptor.construct(Artifact.dataframeContext)

  def summarize(name: String = null)(implicit session: DBSession): serialized.ArtifactSummary = 
  {
    val extras:Map[String,JsValue] = t match {
      case _ => Map.empty
    }
    val base = Artifact.summarize(
                  artifactId = id, 
                  projectId = projectId, 
                  t = t, 
                  created = created, 
                  mimeType = mimeType, 
                  name = Option(name)
                )
    t match {
      case ArtifactType.DATASET => 
        base.toDatasetSummary(
          columns = datasetSchema
                     .zipWithIndex
                     .map { case (field, idx) => 
                              serialized.DatasetColumn(
                                id = idx, 
                                name = field.name, 
                                `type` = field.dataType
                              ) 
                          }
        )
      case _ => base

    }
  }

  def describe(
    name: String = null, 
    offset: Option[Long] = None, 
    limit: Option[Int] = None, 
    forceProfiler: Boolean = false
  )(implicit session: DBSession): serialized.ArtifactDescription = 
  {
    val base = 
      Artifact.summarize(
        artifactId = id, 
        projectId = projectId, 
        t = t, 
        created = created, 
        mimeType = mimeType, 
        name = Option(name)
      )
    t match { 
      case ArtifactType.DATASET => 
        {
          val actualLimit = 
            limit.getOrElse { VizierAPI.MAX_DOWNLOAD_ROW_LIMIT }

          val data =  datasetData(
                        offset = offset, 
                        limit = Some(actualLimit), 
                        forceProfiler = forceProfiler, 
                        includeCaveats = true
                      )
          val rowCount: Long = 
              data.properties
                  .get("count")
                  .map { _.as[Long] }
                  .getOrElse { dataframe.count() }

          Artifact.translateDatasetContainerToVizierClassic(
            projectId = projectId,
            artifactId = id,
            data = data,
            offset = offset.getOrElse { 0 },
            limit = actualLimit,
            rowCount = rowCount,
            base = base
          )
        }
        case ArtifactType.BLOB | ArtifactType.FUNCTION | ArtifactType.FILE | ArtifactType.CHART => 
          base
      }

  }


  def datasetData(
    offset: Option[Long] = None, 
    limit: Option[Int] = None,
    forceProfiler: Boolean = false,
    includeCaveats: Boolean = false
  )(implicit session:DBSession): DataContainer = 
  {
    assert(t.equals(ArtifactType.DATASET))
    val descriptor = datasetDescriptor
    QueryWithCaveats(
      query = descriptor.construct(Artifact.dataframeContext),
      includeCaveats = includeCaveats,
      limit = limit,
      offset = offset,
      computedProperties = descriptor.properties,
      cacheAs = None,
      columns = None
    )
  }

  def datasetPropertyOpt(name: String): Option[JsValue] = 
  {
    assert(t.equals(ArtifactType.FILE))
    datasetDescriptor.properties.get(name)
  }
  def datasetProperty(name: String)(construct: Dataset => JsValue)(implicit session: DBSession): JsValue = 
  {
    assert(t.equals(ArtifactType.DATASET))
    val descriptor = datasetDescriptor
    if(descriptor.properties contains name){
      descriptor.properties(name)
    } else {
      val propValue:JsValue = construct(descriptor)

      // Cache the result
      replaceData(Json.toJson(descriptor.withProperty(name -> propValue)))
      propValue      
    }
  }
  def updateDatasetProperty(name: String, value: JsValue)(implicit session: DBSession): Unit =
  {
    assert(t.equals(ArtifactType.FILE))
    replaceData(Json.toJson(datasetDescriptor.withProperty(name -> value)))
  }

  def filePropertyOpt(name: String): Option[JsValue] = 
  {
    assert(t.equals(ArtifactType.FILE))
    if(data.isEmpty) { None }
    else { (json \ name).asOpt[JsValue] }
  }
  def fileProperty(name: String)(construct: File => JsValue)(implicit session: DBSession): JsValue = 
  {
    filePropertyOpt(name)
      .getOrElse {
        val prop = construct(relativeFile)
        updateFileProperty(name, prop)
        prop
      }
  }
  def updateFileProperty(name: String, value: JsValue)(implicit session: DBSession): Unit =
  {
    assert(t.equals(ArtifactType.FILE))
    val old = if(data.isEmpty) { Map.empty } 
              else { json.as[Map[String, JsValue]] }
    replaceData(
      Json.toJson(
         old ++ Map(name -> value)
      )
    )
  }

  def datasetSchema(implicit session: DBSession):Seq[StructField] =
    datasetProperty("schema") { descriptor =>
      Json.toJson(
        descriptor.construct(Artifact.dataframeContext)
                  .schema:Seq[StructField]
      )
    }.as[Seq[StructField]]

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

  def url: URL = Artifact.urlForArtifact(artifactId = id, projectId = projectId, t = t)

  /**
   * DANGER DANGER DANGER DANGER
   * Replace the data segment of this artifact
   * DANGER DANGER DANGER DANGER
   * 
   * The one situation where this function should ever be used is to add new properties
   * to a file or dataset object.  All other use cases are almost certainly going to 
   * lead to mutable artifacts; in such cases the artifact should be re-created entirely
   * from scratch.
   */
  def replaceData(updated: JsValue)(implicit session: DBSession): Artifact =
    replaceData(updated.toString)
  /**
   * DANGER DANGER DANGER DANGER
   * Replace the data segment of this artifact
   * DANGER DANGER DANGER DANGER
   * 
   * The one situation where this function should ever be used is to add new properties
   * to a file or dataset object.  All other use cases are almost certainly going to 
   * lead to mutable artifacts; in such cases the artifact should be re-created entirely
   * from scratch.
   */
  def replaceData(updated: String)(implicit session: DBSession): Artifact =
    replaceData(updated.getBytes)
  /**
   * DANGER DANGER DANGER DANGER
   * Replace the data segment of this artifact
   * DANGER DANGER DANGER DANGER
   * 
   * The one situation where this function should ever be used is to add new properties
   * to a file or dataset object.  All other use cases are almost certainly going to 
   * lead to mutable artifacts; in such cases the artifact should be re-created entirely
   * from scratch.
   */
  def replaceData(updated: Array[Byte])(implicit session: DBSession): Artifact =
  {
    withSQL {
      val a = Artifact.column
      update(Artifact)
        .set(
          a.data -> updated
        )
        .where.eq(a.id, id)
    }.update.apply()
    copy(data = updated)
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
  def summarize(name: String = null)(implicit session: DBSession): serialized.ArtifactSummary = 
  {
    val extras:Map[String,JsValue] = t match {
      case _ => Map.empty
    }
    val base = Artifact.summarize(
                  artifactId = id, 
                  projectId = projectId, 
                  t = t, 
                  created = created, 
                  mimeType = mimeType, 
                  name = Option(name)
                )
    t match {
      case ArtifactType.DATASET => 
        base.toDatasetSummary(
          columns = materialize.datasetSchema
                     .zipWithIndex
                     .map { case (field, idx) => 
                              serialized.DatasetColumn(
                                id = idx, 
                                name = field.name, 
                                `type` = field.dataType
                              ) 
                          }
        )
      case _ => base

    }
  }
  def absoluteFile: File = Filestore.getAbsolute(projectId, id)
  def relativeFile: File = Filestore.getRelative(projectId, id)
  def file = absoluteFile
  def url: URL = Artifact.urlForArtifact(artifactId = id, projectId = projectId, t = t)

  def materialize(implicit session: DBSession): Artifact = Artifact.get(id, Some(projectId))
}
object ArtifactSummary
  extends SQLSyntaxSupport[ArtifactSummary]
{
  def apply(rs: WrappedResultSet): ArtifactSummary = autoConstruct(rs, (ArtifactSummary.syntax).resultName)
  override def columns = Schema.columns(table).filterNot { _.equals("data") }  
  override def tableName: String = Artifact.tableName
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

  def get(target: Identifier, projectId: Option[Identifier] = None)(implicit session:DBSession): Artifact = getOption(target, projectId).get
  def getOption(target: Identifier, projectId: Option[Identifier] = None)(implicit session:DBSession): Option[Artifact] = 
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
      val a = ArtifactSummary.syntax
      select
        .from(ArtifactSummary as a)
        .where.eq(a.id, target)
    }.map { ArtifactSummary(_) }
     .single.apply()
  }
  def lookupSummaries(targets: Seq[Identifier])(implicit session:DBSession): Seq[ArtifactSummary] =
  {
    withSQL {
      val a = ArtifactSummary.syntax
      select
        .from(ArtifactSummary as a)
        .where.in(a.id, targets)
    }.map { ArtifactSummary(_) }
     .list.apply()
  }

  def urlForArtifact(artifactId: Identifier, projectId: Identifier, t: ArtifactType.T): URL =
    t match {
      case ArtifactType.DATASET => 
        VizierAPI.urls.getDataset(projectId, artifactId)
      case ArtifactType.CHART => 
        VizierAPI.urls.getChartView(projectId, 0, 0, 0, artifactId)
      case _ => 
        VizierAPI.urls.getArtifact(projectId, artifactId)
    }

  def summarize(
    artifactId: Identifier, 
    projectId: Identifier, 
    t: ArtifactType.T, 
    created: ZonedDateTime, 
    mimeType: String, 
    name: Option[String],
    extraHateoas: Seq[(String, URL)] = Seq.empty
  ): serialized.StandardArtifact =
    serialized.StandardArtifact(
      key = artifactId,
      id = artifactId,
      objType = mimeType,
      category = t,
      name = name.getOrElse { s"$t $artifactId" },
      links = HATEOAS((
        Seq(
          HATEOAS.SELF -> urlForArtifact(artifactId, projectId, t)
        ) ++ (t match {
          case ArtifactType.DATASET => Seq(
            HATEOAS.DATASET_FETCH_ALL -> VizierAPI.urls.getDataset(projectId, artifactId, limit = Some(-1)),
            HATEOAS.DATASET_DOWNLOAD  -> VizierAPI.urls.downloadDataset(projectId, artifactId),
            HATEOAS.ANNOTATIONS_GET   -> VizierAPI.urls.getDatasetCaveats(projectId, artifactId)
          )
          case _ => Seq()
        }) ++ extraHateoas
      ):_*)
    )

  /**
   * Translate a Mimir DataContainer to something the frontend UI wants to see
   *
   * The main use of this is to produce Dataset Artifacts for consumption by the
   * frontend via artifact.describe, but we occasionally need to create 
   * similar structures elsewhere (e.g., when we want to cache a DataContainer)
   */
  def translateDatasetContainerToVizierClassic(
    base: serialized.StandardArtifact,
    projectId: Identifier,
    artifactId: Identifier, 
    data: DataContainer,
    offset: Long,
    limit: Int,
    rowCount: Long,
  ): serialized.DatasetDescription = 
  {
    base.toDatasetDescription(
      columns = 
        data.schema.zipWithIndex.map { case (field, idx) =>
          serialized.DatasetColumn(id = idx, name = field.name, `type` = field.dataType)
        },
      rows =
        data.data
            .zip(data.prov)
            .zip(data.rowTaint.zip(data.colTaint))
            .map { case ((row, rowid), (rowCaveatted, attrCaveats)) => 
              serialized.DatasetRow(
                id = rowid,
                values = 
                  data.schema.zip(row)
                      .map { 
                        case (col, v) => SparkPrimitive.encode(v, col.dataType) 
                      },
                rowAnnotationFlags =
                  Some(attrCaveats.map { c => !c }),
                rowIsAnnotated = 
                  Some(rowCaveatted)
              ) 
            },
      rowCount = rowCount,
      offset = offset,
      properties = serialized.PropertyList.toPropertyList(data.properties),
      extraLinks = HATEOAS(
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

  /**
   * Retrieve a mapping of dataframe constructors
   */
  def dataframeContext(implicit session:DBSession): (Identifier => DataFrame) =
    get(_).dataframe
}

