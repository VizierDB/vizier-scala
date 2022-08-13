package info.vizierdb.serialized


case class PythonPackage(
  name: String,
  version: Option[String]
)

object PythonPackage
{
  def apply(nv: (String, String)): PythonPackage = PythonPackage(nv._1, Some(nv._2))
}

case class PythonEnvironment(
  version: String,
  packages: Seq[PythonPackage]
)
