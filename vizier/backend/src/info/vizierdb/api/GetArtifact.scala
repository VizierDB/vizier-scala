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
package info.vizierdb.api

import scalikejdbc._
import play.api.libs.json._
import org.apache.spark.sql.DataFrame

import info.vizierdb.catalog.Artifact
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.caveats.Caveat
import info.vizierdb.spark.caveats.CaveatFormat._
import info.vizierdb.spark.caveats.ExplainCaveats
import info.vizierdb.api.response._
import info.vizierdb.api.handler.{ Handler, ClientConnection }
import info.vizierdb.VizierException
import info.vizierdb.serialized
import info.vizierdb.catalog.CatalogDB
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.SafeExport
import _root_.akka.http.scaladsl.model.ContentType

object GetArtifact
  extends LazyLogging
{
  def getArtifact(projectId: Long, artifactId: Long, expecting: Option[ArtifactType.T]): Option[Artifact] = 
      CatalogDB.withDBReadOnly { implicit session => 
        Artifact.getOption(artifactId, Some(projectId))
      }.filter { artifact => 
        expecting.isEmpty || expecting.get.equals(artifact.t)
      }

  def apply(
    projectId: Identifier,
    artifactId: Identifier,
    offset: Option[Long] = None,
    limit: Option[Int] = None,
    profile: Option[String] = None,
    expecting: Option[ArtifactType.T] = None,
    branchId: Option[Identifier] = None, // Not used... but necessary for routing compatibility
    workflowId: Option[Identifier] = None, // Not used... but necessary for routing compatibility
    modulePosition: Option[Int] = None, // Not used... but necessary for routing compatibility
    name: Option[String] = None,
  ): serialized.ArtifactDescription = 
  {
    val forceProfiler = profile.map { _.equals("true") }.getOrElse(false)
    getArtifact(projectId, artifactId, expecting) match {
      case Some(artifact) => 
        CatalogDB.withDBReadOnly { implicit s => 
          artifact.describe(
            offset = offset, 
            limit = limit, 
            forceProfiler = forceProfiler,
            name = name.getOrElse { null },
          )

          // The parens below are part of the magic... artifact.describe()
          // returns a constructor to handle the database scoping; The 
          // constructor contains a bunch of code that may be slow-running,
          // but does not require database access.  The constructor code
          // needs to be run OUTSIDE of the database access block, which
          // is what happens when the apply method is called below.
        }()
      case None => 
        ErrorResponse.noSuchEntity
    }
  }

  def typed(
    expectedType: ArtifactType.T
  )(
    projectId: Identifier,
    artifactId: Identifier,
    offset: Option[Long] = None,
    limit: Option[Int] = None,
    profile: Option[String] = None,
    branchId: Option[Identifier] = None, // Not used... but necessary for routing compatibility
    workflowId: Option[Identifier] = None, // Not used... but necessary for routing compatibility
    modulePosition: Option[Int] = None, // Not used... but necessary for routing compatibility
    name: Option[String] = None,
  ): serialized.ArtifactDescription = apply(
    projectId = projectId,
    artifactId = artifactId,
    offset = offset,
    limit = limit,
    profile = profile,
    expecting = Some(expectedType),
    name = name,
  )

  object Annotations
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier,
      column: Option[Int],
      row: Option[String],
    ): Seq[Caveat] =
    {
      getArtifact(projectId, artifactId, Some(ArtifactType.DATASET)) match { 
        case Some(artifact) => 
          val df:DataFrame = CatalogDB.withDB { implicit s => artifact.dataframe }()
          ExplainCaveats(
            df,
            rows = row.map { Seq(_) }.getOrElse { null },
            cols = column.map { col => 
                      Seq( df.columns(col) )
                   }.getOrElse { null }
          )
      
        case None => 
          ErrorResponse.noSuchEntity
      }
    }
  }

  object Summary
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier
    ): serialized.ArtifactSummary =
    {
      getArtifact(projectId, artifactId, None) match {
        case Some(artifact) => 
          CatalogDB.withDBReadOnly { implicit s => artifact.summarize() }
        case None => 
          ErrorResponse.noSuchEntity
      }
    } 
  }

  def Dataset(
    projectId: Identifier,
    artifactId: Identifier,
    offset: Option[Long] = None,
    limit: Option[Int] = None,
    profile: Option[String] = None,
    name: Option[String] = None
  ): serialized.DatasetDescription =
    typed(ArtifactType.DATASET)(
      projectId = projectId,
      artifactId = artifactId,
      offset = offset,
      limit = limit,
      profile = profile,
      name = name,
    ).asInstanceOf[serialized.DatasetDescription]

  object CSV
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier,
    ): Response =
    {
      getArtifact(projectId, artifactId, Some(ArtifactType.DATASET)) match {
        case Some(artifact) =>
          val tempFile = java.io.File.createTempFile("artifact_"+artifactId, ".csv")
          if(tempFile.exists()){
            // spark will complain about overwriting the file if it already
            // exists
            tempFile.delete()
          }

          var df:DataFrame = CatalogDB.withDB { implicit s =>
                                artifact.dataframe
                              }()

          df = SafeExport.csv(df)

          df.coalesce(1)
            .write
             .option("header", true)
             .csv(tempFile.toString())

          val csvFile = 
            tempFile.listFiles
                    .find { _.getName.endsWith(".csv") }
                    .getOrElse { 
                      throw new VizierException(
                        "Error Exporting CSV file: No File produced"
                      )
                    }

          FileResponse(
            csvFile, 
            "dataset_"+artifactId+".csv", 
            ContentType.parse("text/csv").right.get,
            () => { 
              tempFile.listFiles
                      .map { _.delete() }
              tempFile.delete() 
            }
          )
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }


  val SANE_FILE_CHARACTERS = "^([\\-_.,a-zA-Z0-9]*)$".r
  object File
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier,
      tail: Option[String] = None,
    ): Response =
    {
      val subpathElements = tail.map { _.split("/") }
      val subpath = subpathElements.map { _.mkString("/") }
      for(element <- subpathElements.toSeq.flatten) { 
        element match {
          case "." | ".." => 
            throw new IllegalArgumentException(s"Invalid subpath $subpath")
          case _ => ()
        }
      }
      getArtifact(projectId, artifactId, Some(ArtifactType.FILE)) match {
        case Some(artifact) => 
        {
          var path = artifact.absoluteFile
          subpath match {
            case None => ()
            case Some(SANE_FILE_CHARACTERS(saneSubpath)) =>
              path = new java.io.File(path, saneSubpath)
            case Some(x) => 
              throw new IllegalArgumentException(s"Invalid subpath $x")
          }
          return FileResponse(
            file = path, 
            contentType = ContentType.parse(artifact.mimeType).right.get, 
            name = artifact.json
                           .as[Map[String, JsValue]]
                           .get("filename")
                           .map { _ .as[String] }
                           .getOrElse { s"unnamed_file_${artifact.id}" }
          )
        }
        case None => 
          return NoSuchEntityResponse() 
      }
    }
  }
}

