package info.vizierdb.ui.components

import scalajs.js
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types.ArtifactType
import info.vizierdb.ui.network.ParameterDescriptor
import info.vizierdb.ui.facades.CodeMirror
import info.vizierdb.ui.rxExtras.OnMount

sealed trait Parameter
{
  val id: String
  val name: String
  val required: Boolean
  val hidden: Boolean
  val root: dom.Node
}

object Parameter
{
  def apply(description: ParameterDescriptor): Parameter =
  {
    description.datatype match {
      case "code" => 
        new CodeParameter(
          description.id, 
          description.name, 
          description.asInstanceOf[js.Dynamic].language.asInstanceOf[String],
          description.required, 
          description.hidden
        )
      case x => 
        new UnsupportedParameter(
          description.id, 
          description.name, 
          description.datatype, 
          description.required, 
          description.hidden
        )
    }
  }
}

class BooleamParameter(
  val id: String, 
  val name: String, 
  default: Option[Boolean], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Boolean: $name")
}

case class CodeParameter(
  val id: String,
  val name: String,
  language: String,
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = 
    div(
      textarea(
        // "code goes here...",
        OnMount { (n: dom.Node) => 
          CodeMirror.fromTextArea(n,
            js.Dictionary(
              "value" -> "this is a test",
              "mode" -> CodeParameter.LANGUAGE.getOrElse(language, "text/plain"),
              "lineNumbers" -> true
            )
          ) 
        }
      )
    )
}
object CodeParameter
{
  val LANGUAGE = Map(
    "python" -> "text/x-python",
    "scala" -> "text/x-scala",
    "sql" -> "text/x-sql",
    "markdown" -> "text/x-markdown"
  )
}

class ColIdParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"ColId: $name")
}

class DatasetParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Dataset: $name")
}

class ArtifactParameter(
  val id: String, 
  val name: String, 
  artifactType: ArtifactType.T,
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Artifact: $name")
}

class DecimalParameter(
  val id: String, 
  val name: String, 
  default: Option[Double], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Decimal: $name")
}

class FileParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"File: $name")
}

class IntParameter(
  val id: String, 
  val name: String, 
  default: Option[Int], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Int: $name")
}

class ListParameter(
  val id: String, 
  val name: String, 
  elements: Seq[Parameter], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"List: $name")
}

class RecordParameter(
  val id: String, 
  val name: String, 
  elements: Seq[Parameter], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Record: $name")
}

class RowIdParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"RowId: $name")
}

class ScalarParaameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Scalar: $name")
}

class EnumerableParameter(
  val id: String, 
  val name: String, 
  values: Seq[(String, String)],
  default: Option[Int],
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Enumerable: $name")
}

class StringParameter(
  val id: String, 
  val name: String, 
  values: Seq[(String, String)],
  default: Option[Int],
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"String: $name")
}

class UnsupportedParameter(
  val id: String, 
  val name: String, 
  dataType: String,
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  val root = span(s"Unsupported parameter type: $dataType")
}
