package info.vizierdb.serialized

case class PackageCommand(
  id: String,
  name: String,
  parameters: Seq[ParameterDescription],
  suggest: Option[Boolean]
)
