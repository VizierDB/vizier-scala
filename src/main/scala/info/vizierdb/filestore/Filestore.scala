package info.vizierdb.filestore

import scalikejdbc._
import info.vizierdb.catalog.Artifact
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.types._

object Filestore
{
  lazy val path = { val d = new File(Vizier.basePath, "files"); if(!d.exists()){ d.mkdir() }; d }

  def get(name: String) = new File(path, name)
  def get(projectId: Identifier, artifactId: Identifier): File =
    get(s"proj_${projectId}_file_${Artifact.nameInBackend(ArtifactType.FILE, artifactId)}")

  def remove(projectId: Identifier, artifactId: Identifier) =
    get(projectId, artifactId).delete()
}