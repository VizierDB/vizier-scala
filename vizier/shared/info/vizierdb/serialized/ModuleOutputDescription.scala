package info.vizierdb.serialized

case class ModuleOutputDescription(
  stderr: Seq[MessageDescription],
  stdout: Seq[MessageDescription]
)
