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
package info.vizierdb.filestore

import scalikejdbc._
import info.vizierdb.catalog.Artifact
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.types._
import java.net.URL

object Filestore
{
  val FILE_DIR = "files"

  lazy val (absolutePath, relativePath) =
  {
    val r = new File(FILE_DIR)
    val a = new File(Vizier.config.basePath(), FILE_DIR)
    if(!a.exists()){ a.mkdir() }
    (a, r)
  }

  def ensureDir(f: File): File = 
  {
    if(!f.exists()){ f.mkdir() }
    return f
  }

  def getAbsolute(name: String) = new File(absolutePath, name)
  def getRelative(name: String) = new File(relativePath, name)
  
  def projectDirName(projectId: Identifier): String = s"proj_$projectId"
  def artifactFileName(artifactId: Identifier): String = s"artifact_${Artifact.nameInBackend(ArtifactType.FILE, artifactId)}"

  def absoluteProjectDir(projectId: Identifier): File = 
    ensureDir(new File(absolutePath, projectDirName(projectId)))
  def relativeProjectDir(projectId: Identifier): File = 
    ensureDir(new File(relativePath, projectDirName(projectId)))

  def getAbsolute(projectId: Identifier, artifactId: Identifier): File =
    new File(absoluteProjectDir(projectId), artifactFileName(artifactId))
  def getRelative(projectId: Identifier, artifactId: Identifier): File =
    new File(relativeProjectDir(projectId), artifactFileName(artifactId))

  def remove(projectId: Identifier, artifactId: Identifier) =
    getAbsolute(projectId, artifactId).delete()

  /**
   * Standardize a path, relative to the defined working directory.
   * @param path     The path to standardize
   * @return         A canonical URL for the file
   * 
   * There are a bunch of "special case" handlers for specific types of
   * file paths; currently related to the "working directory" being an
   * option on the command line.
   */
  def canonicalizePath(path: String): URL =
  {
    if(path.size <= 0) { new URL(path) } 
    else if(path(0) == '/'){ 
      new URL("file://"+path) 
    } else if(!path.contains(":/")) {
      new URL(Vizier.config.workingDirectoryURL, path)
    } else {
      new URL(path)
    }
  }
}

