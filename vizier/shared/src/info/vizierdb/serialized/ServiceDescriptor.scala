package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
import info.vizierdb.nativeTypes.DateTime

case class ServiceDescriptorDefaults(
  maxFileSize: Long,
  maxDownloadRowLimit: Long
)

case class ServiceDescriptorEnvironment(
  name: String,
  version: String,
  backend: String,
  packages: Seq[PackageDescription]
)

case class ServiceDescriptor(
  name: String,
  startedAt: DateTime,
  defaults: ServiceDescriptorDefaults,
  environment: ServiceDescriptorEnvironment,
  links: HATEOAS.T
)