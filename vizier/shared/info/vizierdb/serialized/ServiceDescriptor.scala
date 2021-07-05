package info.vizierdb.serialized

import java.time.ZonedDateTime
import info.vizierdb.shared.HATEOAS

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
  startedAt: ZonedDateTime,
  defaults: ServiceDescriptorDefaults,
  environment: ServiceDescriptorEnvironment,
  links: HATEOAS.T
)