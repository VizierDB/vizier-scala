package info.vizierdb.serialized

case class PackageDescription(
  category: String,
  id: String,
  name: String,
  commands: Seq[PackageCommand]
)