package info.vizierdb.serialized

case class FilesystemObject(
  mimeType: String,
  label: String,
  internalPath: String,
  externalPath: String,
  hasChildren: Boolean,
  icon: Option[String] = None,
)