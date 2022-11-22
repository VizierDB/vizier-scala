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

case class PythonEnvironmentDescriptor(
  name: String,
  id: Identifier,
  pythonVersion: String,
  revision: Identifier,
  packages: Seq[PythonPackage]
)

case class PythonEnvironmentSummary(
  name: String,
  id: Identifier,
  pythonVersion: String,
)

case class PythonSettingsSummary(
  environments: Seq[PythonEnvironmentSummary],
  versions: Seq[String]
)