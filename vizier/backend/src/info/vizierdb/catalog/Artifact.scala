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
import org.apache.spark.sql.AnalysisException
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.akka.VizierServer

case class Artifact(
  id: Identifier,
  projectId: Identifier,
  t: ArtifactType.T,
  created: ZonedDateTime,
  mimeType: String,
  data: Array[Byte]
) extends LazyLogging
{
  /**
   * Interpret the artifact's value as a string
   */
  def string = new String(data)
  // def nameInBackend = Artifact.nameInBackend(t, id)
  /**
   * The absolute path to the artifact's file (if it exists)
   */
  def absoluteFile: File = Filestore.getAbsolute(projectId, id)
  /**
   * The relative (to the current WD) path to the artifact's file (if it exists)
   */
  def relativeFile: File = Filestore.getRelative(projectId, id)
  /**
   * The path to the artifact's file (if it exists)
   */
  def file = absoluteFile
  /**
   * Interpret the artifact's value as a parameter value
   */
  def parameter = json.as[serialized.ParameterArtifact]
  /**
   * Interpret the artifact's value as JSON data
   */
  def json = string match { case "" => Json.obj(); case x => Json.parse(x) }
  /**
   * Retrieve the dataset descriptor of a dataset artifact
   */
  def datasetDescriptor = json.as[Dataset]
  /**
   * Retrieve a spark dataframe for a dataset artifact
   * @param  session   Dataframe construction may need to retrieve a series of other, dependent
   *                   artifacts, so the caller needs to provide a database session.
   */
  def dataframe(implicit session:DBSession):() => DataFrame = 
  {
    val descriptor = datasetDescriptor
    val deps = descriptor.transitiveDependencies(
                 Map(id -> this), 
                 Artifact.get(_:Identifier)
               )
    return { () => descriptor.construct(deps) }
  }

  def dataframeFromContext(ctx: Identifier => Artifact): DataFrame =
    datasetDescriptor.construct(ctx)

  /**
   * Retrieve a summary (an abbreviated [[description]]) of the specified artifact
   * @param  name     (optional) The artifact name to include in the generated summary
   */
  def summarize(name: String = null)(implicit session: DBSession): serialized.ArtifactSummary = 
  {
    val extras:Map[String,JsValue] = t match {
      case _ => Map.empty
    }
    val base = 
      serialized.StandardArtifact(
        key       = id,
        id        = id,
        projectId = projectId,
        objType   = mimeType,
        category  = t,
        name      = Option(name).getOrElse { s"$t $id" },
      )

    t match {
      case ArtifactType.DATASET => 
        try {
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
        } catch { 
          case e:JsResultException =>
            logger.warn(s"Error summarizing dataset: ${e.getMessage()}")
            base
        }
      case _ => base

    }
  }

  /**
   * Retrieve a a full description of the specified artifact
   * @param  name           (optional) The artifact name to include
   * @param  offset         (optional) The index of the first row to include
   * @param  limit          (optional) The number of rows of data to include
   * @param  forceProfiler  (optional) True to ensure that the description includes profiler
   *                        statistics.
   * 
   * The offset and limit arguments only make sense if the artifact is a dataset, and
   * will be ignored otherwise.  
   * 
   * Currently, dataset is the only artifact type that supports profiling, and the
   * forceProfiler argument is ignored for other artifact types.
   */
  def describe(
    name: String = null, 
    offset: Option[Long] = None, 
    limit: Option[Int] = None, 
    forceProfiler: Boolean = false
  )(implicit session: DBSession): () => serialized.ArtifactDescription = 
  {
    val base = 
      serialized.StandardArtifact(
        key       = id,
        id        = id,
        projectId = projectId,
        objType   = mimeType,
        category  = t,
        name      = Option(name).getOrElse { s"$t $id" },
      )

    t match { 
      case ArtifactType.DATASET => 
        {
          val actualLimit = 
            limit.getOrElse { VizierServer.MAX_DOWNLOAD_ROW_LIMIT }

          val dataConstructor =  
              datasetData(
                offset = offset, 
                limit = Some(actualLimit), 
                forceProfiler = forceProfiler, 
                includeCaveats = true
              )(session)

          val df = dataframe

          { () => 
            val data = dataConstructor()
            val rowCount: Long = 
                data.properties
                    .get("count")
                    .map { _.as[Long] }
                    .getOrElse { df().count() }

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
        }

      case ArtifactType.PARAMETER 
         | ArtifactType.VEGALITE =>
      {
        val ret = base.addPayload(json)
        
        { () => ret }
      }

      case ArtifactType.FUNCTION =>
      {
        val ret = base.addPayload(string)
        
        { () => ret }
      }
        
      case _ =>
        () => base
    }

  }

  /**
   * Retrieve the dataset as a classical data container.  Preserved for legacy reasons,
   * and will probably be deprecated at some point.
   * @param  offset         (optional) The index of the first row to include
   * @param  limit          (optional) The number of rows of data to include
   * @param  forceProfiler  (optional) True to ensure that result profiler
   *                        statistics.
   * @param  includeCaveats (optional) True to include caveats in the result
   * @return                A nullary constructor method for the dataset.  This
   *                        method can be invoked outside of the CatalogDB context
   *                        to allow the database lock to be released.
   */
  def datasetData(
    offset: Option[Long] = None, 
    limit: Option[Int] = None,
    forceProfiler: Boolean = false,
    includeCaveats: Boolean = false
  )(implicit session: DBSession): () => DataContainer = 
  {
    assert(t.equals(ArtifactType.DATASET))
    val descriptor = datasetDescriptor
    val deps = descriptor.transitiveDependencies(
                 Map(id -> this), 
                 Artifact.get(_:Identifier)
               )
    return { () => 
      try {
        QueryWithCaveats(
          query = descriptor.construct(deps(_)),
          includeCaveats = false,//includeCaveats,
          limit = limit,
          offset = offset,
          computedProperties = descriptor.properties,
          cacheAs = None,
          columns = None
        )
      } catch {
        case a:AnalysisException if includeCaveats => 
          logger.debug(a.getStackTrace().map { _.toString }.mkString("\n"))
          logger.warn(s"Error applying caveats (${a.getMessage}).  Trying without.")
          QueryWithCaveats(
            query = descriptor.construct(deps(_)),
            includeCaveats = false,
            limit = limit,
            offset = offset,
            computedProperties = descriptor.properties,
            cacheAs = None,
            columns = None
          )
      }
    }

  }

  /**
   * Retrieve a cached dataset property, if it has been computed
   * 
   * This function will fail if the artifact is not a dataset.
   */
  def datasetPropertyOpt(name: String): Option[JsValue] = 
  {
    assert(t.equals(ArtifactType.DATASET))
    datasetDescriptor.properties.get(name)
  }

  /**
   * Retrieve or construct the specified dataset property.
   * @param    name       The name of a dataset property.
   * @param    construct  A rule for constructing the dataset property.
   * @return              The value of the dataset property
   * 
   * This function is used to create/cache dataset properties.  The first time it is
   * called with a specific name, the `construct` function will be called to generate
   * a value.  The value will be cached for subsequent calls.
   */
  def datasetProperty(name: String): Option[JsValue] = 
  {
    assert(t.equals(ArtifactType.DATASET))
    datasetDescriptor.properties.get(name)
  }
  /**
   * Update the specified dataset property
   * @param    name       The name of a dataset property.
   * @param    value      The value to assign to the dataset property.
   */
  def updateDatasetProperty(name: String, value: JsValue)(implicit session: DBSession): Unit =
  {
    assert(t.equals(ArtifactType.DATASET))
    replaceData(Json.toJson(datasetDescriptor.withProperty(name -> value)))
  }

  /**
   * Retrieve a cached file property, if it has been computed
   * 
   * This function will fail if the artifact is not a file.
   */
  def filePropertyOpt(name: String): Option[JsValue] = 
  {
    assert(t.equals(ArtifactType.FILE))
    if(data.isEmpty) { None }
    else { (json \ name).asOpt[JsValue] }
  }
  /**
   * Retrieve or construct the specified file property.
   * @param    name       The name of a file property.
   * @param    construct  A rule for constructing the file property.
   * @return              The value of the file property
   * 
   * This function is used to create/cache file properties.  The first time it is
   * called with a specific name, the `construct` function will be called to generate
   * a value.  The value will be cached for subsequent calls.
   */
  def fileProperty(name: String)(construct: File => JsValue)(implicit session: DBSession): JsValue = 
  {
    filePropertyOpt(name)
      .getOrElse {
        val prop = construct(relativeFile)
        updateFileProperty(name, prop)
        prop
      }
  }
  /**
   * Update the specified file property
   * @param    name       The name of a file property.
   * @param    value      The value to assign to the file property.
   */
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
  /**
   * Retrieve the schema of the specified dataset
   */
  def datasetSchema:Seq[StructField] =
    datasetDescriptor.schema

  /**
   * Delete this artifact.
   * 
   * This function is used exclusively during garbage collection.
   */
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

  /**
   * Retrieve the externally visible download url for this artifact 
   */
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

  def all(projectId: Option[Identifier] = None)(implicit session: DBSession): Iterable[Artifact] =
  {
    withSQL { 
      val b = Artifact.syntax 
      select
        .from(Artifact as b)
        .where(
          sqls.toAndConditionOpt(
            projectId.map { sqls.eq(b.projectId, _) }
          )
        )
    }.map { apply(_) }.iterable.apply()    
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

  def getAll(targets: Iterable[Identifier], projectId: Option[Identifier] = None)(implicit session: DBSession): Seq[Artifact] =
    withSQL {
      val b = Artifact.syntax
      select
        .from(Artifact as b)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.in(b.id, targets.toSeq)),
            projectId.map { sqls.eq(b.projectId, _) }
          )
        )
    }.map { apply(_)}.list.apply() 

  def nameInBackend(t: ArtifactType.T, artifactId: Identifier): String =
    s"${t}_${artifactId}"

  def urlForArtifact(artifactId: Identifier, projectId: Identifier, t: ArtifactType.T): URL =
    t match {
      case ArtifactType.DATASET => 
        Vizier.urls.getDataset(projectId, artifactId)
      case ArtifactType.CHART => 
        Vizier.urls.getChartView(projectId, 0, 0, 0, artifactId)
      case ArtifactType.FILE => 
        Vizier.urls.downloadFile(projectId, artifactId)
      case _ => 
        Vizier.urls.getArtifact(projectId, artifactId)
    }

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
                  Some(attrCaveats.map { c => c }),
                rowIsAnnotated = 
                  Some(rowCaveatted)
              ) 
            },
      rowCount = rowCount,
      offset = offset,
      properties = serialized.PropertyList.toPropertyList(data.properties),
    )
  }
}

