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

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.api.request.Explain
import org.mimirdb.api.CaveatFormat._
import org.mimirdb.api.MimirAPI
import info.vizierdb.api.response._
import info.vizierdb.api.handler.{ Handler, ClientConnection }
import info.vizierdb.VizierException

object GetArtifact
{
  def getArtifact(projectId: Long, artifactId: Long, expecting: Option[ArtifactType.T]): Option[Artifact] = 
      DB.readOnly { implicit session => 
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
  ): Response = 
  {
    val forceProfiler = profile.map { _.equals("true") }.getOrElse(false)
    getArtifact(projectId, artifactId, expecting) match {
      case Some(artifact) => 
        return RawJsonResponse(
          artifact.describe(
            offset = offset, 
            limit = limit, 
            forceProfiler = forceProfiler
          )
        )
      case None => 
        return NoSuchEntityResponse() 
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
  ): Response = apply(
    projectId = projectId,
    artifactId = artifactId,
    offset = offset,
    limit = limit,
    profile = profile,
    expecting = Some(expectedType)
  )

  object Annotations
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier,
      column: Option[Int],
      row: Option[String],
    ): Response =
    {
      getArtifact(projectId, artifactId, Some(ArtifactType.DATASET)) match { 
        case Some(artifact) => 
          return RawJsonResponse(
            Json.toJson(
              Explain(
                s"SELECT * FROM ${artifact.nameInBackend}",
                rows = row.map { Seq(_) }.getOrElse { null },
                cols = column.map { col => 
                          Seq(
                            MimirAPI.catalog.get(artifact.nameInBackend)
                                    .schema(col)
                                    .name
                          )
                       }.getOrElse { null }
              )
            )
          )
      case None => 
        return NoSuchEntityResponse() 
      }
    }
  }

  object Summary
  {
    def apply(
      projectId: Identifier,
      artifactId: Identifier
    ): Response =
    {
      getArtifact(projectId, artifactId, None) match {
        case Some(artifact) => 
          return RawJsonResponse(
            artifact.summarize()
          )
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }

  object CSV extends Handler
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
          MimirAPI.catalog.get(artifact.nameInBackend)
                  .coalesce(1)
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
            "text/csv",
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
  object File extends Handler
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
            contentType = artifact.mimeType, 
            name = artifact.jsonData
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

