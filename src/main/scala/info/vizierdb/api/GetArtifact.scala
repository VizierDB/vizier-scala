/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.api.request.Explain
import org.mimirdb.api.CaveatFormat._
import org.mimirdb.api.MimirAPI
import info.vizierdb.api.response._

case class GetArtifactRequest(
  projectId: Identifier, 
  artifactId: Identifier, 
  expectedType: Option[ArtifactType.T] = None, 
  offset: Option[Long] = None, 
  limit: Option[Int] = None, 
  forceProfiler: Boolean = false
)
  extends Request
{
  def getArtifact(expecting: Option[ArtifactType.T] = expectedType): Option[Artifact] = 
      DB.readOnly { implicit session => 
        Artifact.lookup(artifactId, Some(projectId))
      }.filter { artifact => 
        expecting.isEmpty || expecting.get.equals(artifact.t)
      }

  def handle: Response = 
  {
    getArtifact() match {
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

  case class Annotations(columnId: Option[Int] = None, rowId: Option[String] = None) extends Request
  {
    def handle: Response =
    {
      getArtifact(Some(ArtifactType.DATASET)) match { 
        case Some(artifact) => 
          return RawJsonResponse(
            Json.toJson(
              Explain(
                s"SELECT * FROM ${artifact.nameInBackend}",
                rows = rowId.map { Seq(_) }.getOrElse { null },
                cols = columnId.map { col => 
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

  object Summary extends Request
  {
    def handle: Response = 
    {
      getArtifact() match {
        case Some(artifact) => 
          return RawJsonResponse(
            artifact.summarize()
          )
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }

  object CSV extends Request
  {
    def handle: Response = 
    {
      getArtifact(Some(ArtifactType.DATASET)) match {
        case Some(artifact) => 
          ???
        case None => 
          return NoSuchEntityResponse() 
      }
    } 
  }


  val SANE_FILE_CHARACTERS = "^([\\-_.,a-zA-Z0-9]*)$".r
  case class File(subpath: Option[String] = None) extends Request
  {
    def handle: Response =
    {
      getArtifact(Some(ArtifactType.FILE)) match {
        case Some(artifact) => 
        {
          var path = artifact.file
          subpath match {
            case None => ()
            case Some(SANE_FILE_CHARACTERS(saneSubpath)) =>
              path = new java.io.File(path, saneSubpath)
            case Some(x) => 
              throw new IllegalArgumentException(s"Invalid subpath $x")
          }
          return FileResponse(
            file = path, 
            mimeType = artifact.mimeType, 
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

