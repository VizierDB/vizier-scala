package info.vizierdb.serialized

import info.vizierdb.types._

case class PythonPackage(
  name: String,
  version: Option[String]
)

object PythonPackage
{
  def apply(nv: (String, String)): PythonPackage = PythonPackage(nv._1, Some(nv._2))
}

case class PythonEnvironment(
  pythonVersion: String,
  revision: Identifier,
  packages: Seq[PythonPackage]
)
