package info.vizierdb.catalog

import info.vizierdb.types._

class Artifact(
  val id: Identifier,
  val data: Array[Byte]
)
{
  def string = new String(data)
}
object Artifact
{
  def get(id: Identifier): Option[Artifact] = ???
  def make(t: ArtifactType.T, data: Array[Byte]): Artifact = ???
}