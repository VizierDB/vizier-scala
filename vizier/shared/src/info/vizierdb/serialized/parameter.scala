package info.vizierdb.serialized

import info.vizierdb.types.ArtifactType
import info.vizierdb.nativeTypes.JsValue

sealed trait ParameterDescription
{
  def id: String
  def name: String
  def datatype: String
  def hidden: Boolean
  def required: Boolean
  def parent: Option[String]
  def index: Int
  def default: Option[JsValue]
}

case class SimpleParameterDescription(
  id: String,
  name: String,
  datatype: String,
  hidden: Boolean,
  required: Boolean,
  parent: Option[String],
  index: Int,
  default: Option[JsValue]
) extends ParameterDescription

case class CodeParameterDescription(
  id: String,
  name: String,
  datatype: String,
  hidden: Boolean,
  required: Boolean,
  parent: Option[String],
  index: Int,
  default: Option[JsValue],
  language: String
) extends ParameterDescription

case class ArtifactParameterDescription(
  id: String,
  name: String,
  datatype: String,
  hidden: Boolean,
  required: Boolean,
  parent: Option[String],
  index: Int,
  default: Option[JsValue],
  artifactType: ArtifactType.T
) extends ParameterDescription

case class EnumerableValueDescription(
  isDefault: Boolean,
  text: String,
  value: String
)

case class EnumerableParameterDescription(
  id: String,
  name: String,
  datatype: String,
  hidden: Boolean,
  required: Boolean,
  parent: Option[String],
  index: Int,
  default: Option[JsValue],
  values: Seq[EnumerableValueDescription]
) extends ParameterDescription