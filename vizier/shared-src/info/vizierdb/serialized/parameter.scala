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

class ParameterDescriptionTree(
  val parameter: ParameterDescription,
  val children: Seq[ParameterDescriptionTree]
)
object ParameterDescriptionTree
{
  def apply(parameters: Seq[ParameterDescription]): Seq[ParameterDescriptionTree] =
    buildTree(parameters.groupBy { _.parent }, None)

  def buildTree(
    parameters: Map[Option[String], Seq[ParameterDescription]], 
    root: Option[ParameterDescription]
  ): Seq[ParameterDescriptionTree] =
                  // get the direct descendants of the root (if one exists)
    parameters.get(root.map { _.id })
                  // if no entry, then there are no children of this root
              .getOrElse { Seq.empty }
                  // for each child of the root node
              .map { child => 
                  // create a tree node for the child
                new ParameterDescriptionTree(
                  child, 
                  // by recursively finding the descendants of the child (if any exist)
                  buildTree(parameters, Some(child))
                )
              }
                    // and finally we want the children as a sequence
              .toSeq
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