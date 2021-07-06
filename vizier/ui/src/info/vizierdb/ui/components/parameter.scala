package info.vizierdb.ui.components

import scala.collection.mutable
import scalajs.js
import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types.ArtifactType
import info.vizierdb.ui.facades.{ CodeMirror, CodeMirrorEditor }
import info.vizierdb.ui.rxExtras.{ OnMount, RxBuffer, RxBufferView }
import info.vizierdb.util.{ Logger, Logging }
import info.vizierdb.serialized

class ParameterError(msg: String, val parameter: Parameter) extends Exception(msg)

/**
 * A parameter for a command.  Primarily used by [[ModuleEditor]]
 */
sealed trait Parameter
{
  /**
   * A unique identifier for the parameter; the key in the module arguments
   */
  val id: String
  
  /**
   * A human-readable name for the parameter.
   */
  val name: String
  
  /**
   * If true, the parameter value must be set before the form is submitted
   */
  val required: Boolean
  
  /**
   * If true, the value is hidden.  Hidden arguments are used to pass state
   * through different iterations of the workflow, and/or backwards compatibility.
   */
  val hidden: Boolean

  /**
   * The DOM [[Node]] used to display the parameter's input widget
   */
  val root: dom.Node

  /**
   * The current value of this parameter's input widget
   */
  def value: Any

  /**
   * Update the current value of this parameter's input widget
   */
  def set(v: Any)

  /**
   * Encode the parameter and its value as a [[ModuleArgument]]
   */
  def toArgument: serialized.CommandArgument =
    js.Dictionary( "id" -> id, "value" -> value ).asInstanceOf[serialized.CommandArgument]

  /**
   * Callbacks to trigger when the value of the element changes
   */
  private val changeHandlers = mutable.Buffer[dom.Event => Unit]()

  /**
   * Register code to run when the element's value changes
   */
  def onChange(handler: dom.Event => Unit) =
    changeHandlers.append(handler)

  /**
   * Generic utility constructor for DOM [[Node]]s for the parameter's field.
   */
  def field(
    basetag: String,
    attrs: AttrPair*
  )(elems: Frag*):Frag = 
  {
    val identity = s"parameter_${Parameter.nextInputId}"
    div(`class` := "parameter",
      label(attr("for") := identity, name),
      tag(basetag)(
        attrs, 
        `class` := Parameter.PARAMETER_WIDGET_CLASS, 
        attr("id") := identity, 
        attr("name") := name, 
        elems
      ),
      onchange := { (e:dom.Event) => changeHandlers.foreach { _(e) } }
    )
  }

  /**
   * &lt;input&gt;-tag utility constructor for the parameter's field
   */
  def input(
    attrs: AttrPair*
  ): Frag = field("input", attrs:_*)()

  /**
   * &lt;select&gt;-tag utility constructor for the parameter's field
   */
  def pulldown(
    selected: Int
  )(options: (String, String)*): Frag =
    field("select")(
      options.zipWithIndex.map { case ((description, value), idx) => 
        option(
          attr("value") := value, 
          if(idx == selected) { attr("selected", raw = true) := "" } else { "" },
          description
        )
      }:_*
    )

  def inputNode[T <: dom.Node]: T = 
    findArgumentNode(root).get.asInstanceOf[T]

  def findArgumentNode(search: dom.Node): Option[dom.Node] = 
  {
    if(search.attributes.equals(js.undefined)) { return None }
    val classAttr = 
      search.attributes.getNamedItem("class")
    val isCommand = 
      Option(classAttr).map { _.value.split(" ") contains Parameter.PARAMETER_WIDGET_CLASS }
                       .getOrElse { false }
    if(isCommand) {
      return Some(search)
    } else {
      for(i <- 0 until search.childNodes.length){
        val r = findArgumentNode(search.childNodes(i))
        if(r.isDefined) { return r }
      }
      return None
    }
  }
}

/**
 * Utility methods for decoding [[Parameter]] instances
 */
object Parameter
  extends Logging
{
  val PARAMETER_WIDGET_CLASS = "command-argument"

  /**
   * Decode a [[ParameterDescriptor]] into a [[Parameter]] for use with the
   * specified [[ModuleEditor]]
   */
  def apply(tree: serialized.ParameterDescriptionTree, editor: ModuleEditor)
           (implicit owner: Ctx.Owner): Parameter =
  {
    tree.parameter match {
      case param: serialized.SimpleParameterDescription =>
        param.datatype match {
          case "colid"   => new ColIdParameter(param, editor.module.visibleArtifacts.flatMap { x => x }, editor.parameters)
          case "list"    => new ListParameter(param, tree.children, this.apply(_, editor))
          case "record"  => new RecordParameter(param, tree.children, this.apply(_, editor))
          case "string"  => new StringParameter(param)
          case "int"     => new IntParameter(param)
          case "decimal" => new DecimalParameter(param)
          case "bool"    => new BooleanParameter(param)
          case "rowid"   => new RowIdParameter(param)
          case "fileid"  => new FileParameter(param)
          case _         => new UnsupportedParameter(param)
        }

      case param: serialized.CodeParameterDescription =>
        new CodeParameter(param)

      case param: serialized.ArtifactParameterDescription =>
        new ArtifactParameter(param, editor.module
                                          .visibleArtifacts
                                          .map { _().mapValues { _.t } })
      case param: serialized.EnumerableParameterDescription =>
        new EnumerableParameter(param)

      case _ => new UnsupportedParameter(tree.parameter)
    }
  }

  private var nextInputIdValue: Long = -1l
  /**
   * Each parameter has a unique identifier; Allocate a fresh one
   */
  def nextInputId = { nextInputIdValue += 1; nextInputIdValue }
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A Boolean-valued parameter
 */
class BooleanParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{


  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    input(`type` := "checkbox").render
  def value = 
    inputNode[dom.html.Input].value.toBoolean
  override def set(v: Any): Unit =
    inputNode[dom.html.Input].value = v.asInstanceOf[Boolean].toString
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter that accepts a block of code.  Implemented with CodeMirror
 */
case class CodeParameter(
  val id: String,
  val name: String,
  language: String,
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{


  def this(parameter: serialized.CodeParameterDescription)
  {
    this(
      parameter.id, 
      parameter.name, 
      parameter.language,
      parameter.required, 
      parameter.hidden
    )
  }

  var editor: CodeMirrorEditor = null

  val root = 
    div(
      textarea(
        // "code goes here...",
        OnMount { (n: dom.Node) => 
          editor = CodeMirror.fromTextArea(n,
            js.Dictionary(
              "value" -> "this is a test",
              "mode" -> CodeParameter.CODEMIRROR_FORMAT.getOrElse(language, "text/plain"),
              "lineNumbers" -> true
            )
          ) 
        }
      )
    )
  def value = 
    Option(editor).map { _.getValue }
                  .getOrElse { "" }
  override def set(v: Any): Unit = 
    Option(editor).map { _.setValue(v.asInstanceOf[String]) }
}
object CodeParameter
{
  /**
   * Translation table from Vizier-native format descriptions to CodeMirror's identifier
   */
  val CODEMIRROR_FORMAT = Map(
    "python" -> "text/x-python",
    "scala" -> "text/x-scala",
    "sql" -> "text/x-sql",
    "markdown" -> "text/x-markdown"
  )
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter to select a column from the currently selected dataset.
 * 
 * A reactive list of [[DatasteColumn]]s determines the list of columns shown.  This is
 * typically derived from the first [[DatsetParameter]] in the parameter list, and the
 * datasets visible at this point in the workflow.
 */
class ColIdParameter(
  val id: String, 
  val name: String, 
  schema: Rx[Seq[serialized.DatasetColumn]],
  val required: Boolean,
  val hidden: Boolean
) (implicit owner: Ctx.Owner) extends Parameter
{

  def this(
    parameter: serialized.ParameterDescription, 
    datasets: Rx[Map[String, serialized.ArtifactSummary]], 
    otherParameters: Seq[Parameter]
  )
          (implicit owner: Ctx.Owner)
  {
    this(
      parameter.id,
      parameter.name,
      otherParameters.flatMap {
        case dsParameter:ArtifactParameter 
                if dsParameter.artifactType == ArtifactType.DATASET 
                   => Some(dsParameter)
        case _     => None
      }.headOption
       .map { dsParameter =>
          Rx {
            dsParameter.selectedDataset() match {
              case None => Seq.empty
              case Some(dsName) => 
                datasets().get(dsName) match {
                  case None => 
                    Parameter.logger.warn(s"ColIdParameter $name used with an undefined artifact")
                    Seq.empty
                  case Some(ds:serialized.DatasetSummary) => ds.columns
                  case Some(ds:serialized.DatasetDescription) => ds.columns
                  case Some(_) => 
                    Parameter.logger.warn(s"ColIdParameter $name used with a non-dataset artifact")
                    Seq.empty
                }
            }
          }
      }.getOrElse { 
        Parameter.logger.warn(s"ColIdParameter $name used with out an associated dataset")
        Var(Seq.empty) 
      },
      parameter.required,
      parameter.hidden
    )
  }

  val root = span(
    Rx {
      pulldown(0)(
        (
          ("---" -> "") +:
          schema().map { col => 
            col.name -> col.id.toString
          }
        ):_*
      )
    }
  )
  def value = 
    inputNode[dom.html.Select].value
  override def set(v: Any): Unit = 
    inputNode[dom.html.Select].value = v.asInstanceOf[String]

}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter to select an artifact.
 * 
 * A reactive list of artifacts must be provided to the parameter.  This is typically
 * derived from the list of artifacts visible at the point in the workflow where
 * the module is being inserted.
 */
class ArtifactParameter(
  val id: String, 
  val name: String, 
  val artifactType: ArtifactType.T,
  artifacts: Rx[Map[String, ArtifactType.T]],
  val required: Boolean,
  val hidden: Boolean,
)(implicit owner: Ctx.Owner) extends Parameter
{

  def this(parameter: serialized.ArtifactParameterDescription, artifacts: Rx[Map[String, ArtifactType.T]])
          (implicit owner: Ctx.Owner)
  {
    this(
      parameter.id, 
      parameter.name, 
      parameter.artifactType,
      artifacts,
      parameter.required, 
      parameter.hidden
    )
  }

  val selectedDataset = Var[Option[String]](None)

  onChange { e:dom.Event => 
    selectedDataset() = 
      e.asInstanceOf[js.Dynamic]
       .target
       .value
       .asInstanceOf[String] match {
          case "" => None
          case x => Some(x)
       }
  }

  val root = span(
    Rx { 
      pulldown(0)(
        (
          Seq("---" -> "") ++ 
          artifacts().filter { _._2 == artifactType }
                     .map { x => x._1 -> x._1 }
        ):_*
      )
    }
  )
  def value = 
    inputNode[dom.html.Select].value match {
      case "" => null
      case x => x
    }
  override def set(v: Any): Unit = 
    inputNode[dom.html.Select].value = v.asInstanceOf[String]
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A Decimal-valued parameter
 */
class DecimalParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    input(`type` := "number", step := "0.01").render.asInstanceOf[dom.html.Input]
  def value = 
    inputNode[dom.html.Input].value.toDouble
  override def set(v: Any): Unit = 
    inputNode[dom.html.Input].value = v.asInstanceOf[Float].toString
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A file selector
 */
class FileParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
)(implicit owner: Ctx.Owner) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
          (implicit owner: Ctx.Owner)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }

  val DEFAULT_BODY_TEXT = "Drop a file here"
  val bodyText = Var(span(DEFAULT_BODY_TEXT))

  var uploadedFileId = "no-file-uploaded"
  var uploadedFileName = "not-a-file"

  val dragAndDropField:dom.Node = 
    div(`class` := "file-drop-area",
      bodyText,
      ondrop := { (e:dom.DragEvent) => 
        bodyText() = span("file dropped")
        e.preventDefault()
      },
      ondragover := { (e:dom.DragEvent) => 
        bodyText() = span("file in drop zone")
        e.preventDefault()
      },
      ondragleave := { (e:dom.DragEvent) => 
        bodyText() = span(DEFAULT_BODY_TEXT)
        e.preventDefault()
      }
    )
  val urlField:dom.Node =
  {
    val identity = s"parameter_${Parameter.nextInputId}"
    div(`class` := "parameter",
      label(attr("for") := identity, "URL: "),
      tag("input")(
        `type` := "string",
        `class` := Parameter.PARAMETER_WIDGET_CLASS, 
        attr("id") := identity, 
        attr("name") := "URL"
      )
    )
  }

  val displays = Seq[dom.Node](
    dragAndDropField,
    urlField
  )

  val mode = Var(0)

  def tab(name: String, idx: Int) = 
      Rx { 
        if(mode().equals(idx)){
          button(
            name, 
            `class` := "tab selected", 
            onclick := { (e: dom.MouseEvent) => mode() = idx }
          )
        } else {
          button(
            name, 
            `class` := "tab not-selected", 
            onclick := { (e: dom.MouseEvent) => mode() = idx }
          )
        }
      }

  val root = fieldset(
    `class` := "upload-dataset",
    legend(name),
    tab("Upload File", 0),
    tab("Load URL", 1),
    mode.map { displays(_) }
  )
  def value =
    mode.now match {
      case 0 => js.Object("fileid" -> uploadedFileId, "filename" -> uploadedFileName)
      case 1 => js.Object("url" -> inputNode[dom.html.Input].value)
      case _ => null
    }
  def set(v: Any): Unit = 
    ???
}

/////////////////////////////////////////////////////////////////////////////

/**
 * An Integer-valued parameter
 */
class IntParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    input(`type` := "number", step := "1").render.asInstanceOf[dom.html.Input]
  def value = 
    inputNode[dom.html.Input].value.toInt
  def set(v: Any): Unit = 
    inputNode[dom.html.Input].value = v.asInstanceOf[Int].toString
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A nested list of parameters.  
 * 
 * The elements field defines the parameters that appear in each row of the list.
 */
class ListParameter(
  val id: String, 
  val name: String, 
  titles: Seq[String],
  elements: Seq[() => Parameter], 
  val required: Boolean,
  val hidden: Boolean
)(implicit owner: Ctx.Owner)
  extends Parameter
{
  def this(
    parameter: serialized.ParameterDescription, 
    children: Seq[serialized.ParameterDescriptionTree], 
    getParameter: serialized.ParameterDescriptionTree => Parameter
  )(implicit owner: Ctx.Owner)
  {
    this(
      parameter.id,
      parameter.name, 
      children.map { _.parameter.name },
      children.map { x => () => getParameter(x) },
      parameter.required,
      parameter.hidden
    )
  }

  val rows = RxBuffer[Seq[Parameter]]( tentativeRow() )
  val rowView = RxBufferView(tbody(), 
    rows.rxMap { row =>  
      tr( 
        row.map { _.root }.map { td(_) } ,
        button(
          "X",
          onclick := { e:dom.MouseEvent => 
            val idx = rows.indexOf(row)
            if(idx < rows.length - 1 && idx >= 0){
              rows.remove(idx)
            }
          }
        )
      )
    })
  def lastRow = Var(rows.last)

  def tentativeRow(): Seq[Parameter] =
  {
    val row = elements.map { _() }
    row.foreach { _.onChange { e => touchRow(row) } }
    row
  }

  def touchRow(row: Seq[Parameter])
  {
    if(row == lastRow.now) { 
      val newLast = tentativeRow()
      rows.append(newLast)
      lastRow() = newLast
    }
  }

  val root = 
    fieldset(
      legend(name),
      table(
        thead(
          tr(
            titles.map { th(_) },
            th("")
          )
        ),
        rowView.root,
      )
    )
  def value = 
    rows.toSeq
        .take(rows.length-1)
        .map { _.map { _.toArgument } }
  def set(v: Any): Unit = 
    ???
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A group of parameters.
 * 
 * The elements field contains the parameters in the group.
 */
class RecordParameter(
  val id: String, 
  val name: String, 
  elements: Seq[Parameter], 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(
    parameter: serialized.ParameterDescription, 
    children: Seq[serialized.ParameterDescriptionTree],
    getParameter: serialized.ParameterDescriptionTree => Parameter
  )(implicit owner: Ctx.Owner)
  {
    this(
      parameter.id,
      parameter.name, 
      children.map { getParameter(_) },
      parameter.required,
      parameter.hidden
    )
  }
  val root = 
    fieldset(
      legend(name),
      ul(
        elements.map { _.root }.map { li(_) }
      )
    )
  def value = 
    elements.map { _.toArgument }
  def set(v: Any): Unit = 
    ???
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter indicating a specific row
 */
class RowIdParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    input(`type` := "number", step := "1").render.asInstanceOf[dom.html.Input]
  def value = 
    inputNode[dom.html.Input].value
  def set(v: Any): Unit = 
    inputNode[dom.html.Input].value = v.asInstanceOf[String]

}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter with a limited set of options
 */
class EnumerableParameter(
  val id: String, 
  val name: String, 
  values: Seq[serialized.EnumerableValueDescription],
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.EnumerableParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      values = parameter.values,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    pulldown(
      values.zipWithIndex
            .find { _._1.isDefault }
            .map { _._2 }
            .getOrElse { 0 }
    )(values.map { v => v.text -> v.value }:_*)
      .render.asInstanceOf[dom.html.Select]

  def value = 
    inputNode[dom.html.Select].value
  def set(v: Any): Unit = 
    inputNode[dom.html.Select].value = v.asInstanceOf[String]

}

/////////////////////////////////////////////////////////////////////////////

/**
 * A string-valued parameter
 */
class StringParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root = 
    input(`type` := "text").render.asInstanceOf[dom.html.Input]
  def value =
    inputNode[dom.html.Input].value
  def set(v: Any): Unit = 
    inputNode[dom.html.Input].value = v.asInstanceOf[String]
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A fallback class to use a parameter of an unknown type
 */
class UnsupportedParameter(
  val id: String, 
  val name: String, 
  dataType: String,
  val required: Boolean,
  val hidden: Boolean
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      parameter.id, 
      parameter.name, 
      parameter.datatype,
      parameter.required, 
      parameter.hidden
    )
  }
  val root = span(s"Unsupported parameter type: $dataType")
  def value = null
  def set(v: Any): Unit = {}
}