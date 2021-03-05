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
package info.vizierdb.filestore

import scalikejdbc._
import info.vizierdb.catalog.Artifact
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.types._

object Filestore
{
  lazy val path = { 
    val d = new File(Vizier.config.basePath(), "files")
    if(!d.exists()){ d.mkdir() }
    d 
  }

  def get(name: String) = new File(path, name)
  def projectDir(projectId: Identifier): File = 
  {
    val dir = get(s"proj_$projectId")
    if(!dir.exists){ dir.mkdir() }
    return dir
  }
  def get(projectId: Identifier, artifactId: Identifier): File =
    new File(
      projectDir(projectId = projectId),
      s"artifact_${Artifact.nameInBackend(ArtifactType.FILE, artifactId)}"
    )

  def remove(projectId: Identifier, artifactId: Identifier) =
    get(projectId, artifactId).delete()
}
