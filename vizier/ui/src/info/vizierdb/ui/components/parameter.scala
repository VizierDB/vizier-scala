package info.vizierdb.ui.components

import scala.collection.mutable
import play.api.libs.json._
import scalajs.js
import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.ui.facades.{ CodeMirror, CodeMirrorEditor }
import info.vizierdb.ui.rxExtras.{ OnMount, RxBuffer, RxBufferView }
import info.vizierdb.util.{ Logger, Logging }
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.ui.components.snippets.PythonSnippets
import info.vizierdb.ui.components.snippets.SnippetsBase
import info.vizierdb.ui.components.snippets.ScalaSnippets
import org.scalajs.dom.Node
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner

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
  def value: JsValue

  /**
   * Update the current value of this parameter's input widget
   */
  def set(v: JsValue)

  /**
   * Encode the parameter and its value as a [[ModuleArgument]]
   */
  def toArgument: serialized.CommandArgument =
    serialized.CommandArgument(id, value)

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
  def apply(tree: serialized.ParameterDescriptionTree, editor: DefaultModuleEditor)
           (implicit owner: Ctx.Owner): Parameter =
  {
    def visibleArtifactsByType = editor.delegate
                                       .visibleArtifacts
                                       .map { _.mapValues { _._1.t } }
    def visibleArtifacts       = editor.delegate
                                       .visibleArtifacts
                                       .map { _.mapValues { _._1 } } 


    tree.parameter match {
      case param: serialized.SimpleParameterDescription =>
        param.datatype match {
          case "colid"       => new ColIdParameter(param, visibleArtifacts, editor.selectedDataset)
          case "list"        => new ListParameter(param, tree.children, this.apply(_, editor), visibleArtifacts)
          case "numericalfilter" => new NumericalFilterParameter(param)
          case "label"       => new LabelParameter(param)
          case "record"      => new RecordParameter(param, tree.children, this.apply(_, editor))
          case "string"      => new StringParameter(param)
          case "int"         => new IntParameter(param)
          case "decimal"     => new DecimalParameter(param)
          case "bool"        => new BooleanParameter(param)
          case "rowid"       => new RowIdParameter(param)
          case "fileid"      => new FileParameter(param)
          case "dataset"     => new ArtifactParameter(param, ArtifactType.DATASET, visibleArtifactsByType)
          case "datatype"    => new DataTypeParameter(param)
          case "json"        => new JsonParameter(param)
          case _             => new UnsupportedParameter(param)
        }

      case param: serialized.CodeParameterDescription =>
        param.datatype match {
          case "environment" => new EnvironmentParameter(param)
          case _ => new CodeParameter(param)
        }

      case param: serialized.ArtifactParameterDescription =>
        new ArtifactParameter(param, visibleArtifactsByType)
        
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


  def this(id: String, name: String, required: Boolean, hidden: Boolean, default: Boolean)
  {
    this(id, name, required, hidden)
    set(JsBoolean(default))
  }

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
  {
    JsBoolean(inputNode[dom.html.Input].checked)
  }

  override def set(v: JsValue): Unit =
    inputNode[dom.html.Input].checked = v.as[Boolean]
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
  val hidden: Boolean,
  val startWithSnippetsHidden: Boolean = false
)(implicit owner: Ctx.Owner) extends Parameter
{


  def this(parameter: serialized.CodeParameterDescription)(implicit owner: Ctx.Owner) 
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
  var initialValue: String = null

  var onInit: (CodeMirrorEditor => Unit) = { _ => }

  val hideSnippets = Var[Boolean](startWithSnippetsHidden)
 
  val snippets = 
    div(
      CodeParameter.SNIPPETS.get(language).map { _.apply { snippet => 
        editor.replaceSelection("\n"+snippet)
      }}
    )

  val root = 
    div(
      width := "100%",
      textarea(
        // "code goes here...",
        OnMount { (n: dom.Node) => 
          editor = CodeMirror.fromTextArea(n,
            js.Dictionary(
              "mode" -> CodeParameter.CODEMIRROR_FORMAT.getOrElse(language, "text/plain"),
              "lineNumbers" -> true,
              "viewportMargin" -> Double.PositiveInfinity,
            )
          ) 
          if(initialValue != null) { editor.setValue(initialValue) }
          onInit(editor)
        }
      ),
      hideSnippets.map { 
        case true => 
          // println("Hiding snippets!")
          div(display := "hidden")
        case false => 
          // println("Showing snippets!")
          snippets
      }.reactive
    ).render
  def value = 
    JsString(
      Option(editor).map { _.getValue }
                    .getOrElse { "" }
    )

  def set(v: String): Unit =
  {
    initialValue = v
    if(editor != null) { editor.setValue(v) }
  }

  override def set(v: JsValue): Unit = 
    set(v.as[String])
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

  val SNIPPETS = Map[String, SnippetsBase](
    "python" -> PythonSnippets,
    "scala" -> ScalaSnippets
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
    selectedDataset: Rx[Option[String]]
  )
          (implicit owner: Ctx.Owner)
  {
    this(
      parameter.id,
      parameter.name,
      Rx {
        selectedDataset() match {
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
      },
      parameter.required,
      parameter.hidden
    )
  }

  val selectedColumn = Var[Option[Int]](None)

  onChange { e:dom.Event => 
    selectedColumn() = 
      inputNode[dom.html.Select].value match { 
        case "" => None
        case x => Some(x.toInt)
      }
  }

  val root = span(
    Rx {
      pulldown(selectedColumn().map { _ + 1 }.getOrElse(0))(
        (
          ("---" -> "") +:
          schema().map { col => 
            col.name -> col.id.toString
          }
        ):_*
      )
    }.reactive
  )
  def value = 
    JsNumber(inputNode[dom.html.Select].value.toInt)
  override def set(v: JsValue): Unit = 
  {
    selectedColumn() = v.asOpt[Int]
  }

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
  def this(parameter: serialized.SimpleParameterDescription, artifactType: ArtifactType.T, artifacts: Rx[Map[String, ArtifactType.T]])
          (implicit owner: Ctx.Owner)
  {
    this(
      parameter.id, 
      parameter.name, 
      artifactType,
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
    }.reactive
  )
  def value = 
    inputNode[dom.html.Select].value match {
      case "" => JsNull
      case x => JsString(x)
    }
  override def set(v: JsValue): Unit = 
  {
    selectedDataset() = v.asOpt[String]
    inputNode[dom.html.Select].value = v.asOpt[String].getOrElse { "" }
  }
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
    JsNumber(inputNode[dom.html.Input].value.toDouble)
  override def set(v: JsValue): Unit = 
    inputNode[dom.html.Input].value = v.as[Float].toString
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

  var uploadedFileId: Identifier = -1
  var uploadedFileName = "not-a-file"

  val dragAndDropField:dom.Node = 
    div(`class` := "file-drop-area",
      bodyText.reactive,
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
    tab("Upload File", 0).reactive,
    tab("Load URL", 1).reactive,
    mode.map { displays(_) }.reactive
  )
  def value =
    mode.now match {
      case 0 => Json.obj("fileid" -> uploadedFileId, "filename" -> uploadedFileName)
      case 1 => Json.obj("url" -> inputNode[dom.html.Input].value)
      case _ => JsNull
    }
  def set(v: JsValue): Unit = 
  {
    val url = (v \ "url").asOpt[String]
    val fileid = (v \ "fileid").asOpt[Identifier]
    val filename = (v \ "filename").asOpt[String]

    if(url.isDefined){
      mode() = 1
      inputNode[dom.html.Input].value = url.get 
    } else if(fileid.isDefined) {
      mode() = 0
      uploadedFileId = fileid.get
      uploadedFileName = filename.getOrElse { "uploaded-file" }
    } else {
      println("Received invalid file parameter... leaving the upload space blank.")
    }
  }
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
    JsNumber(inputNode[dom.html.Input].value.toInt)
  def set(v: JsValue): Unit = 
    inputNode[dom.html.Input].value = v.as[Int].toString
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
  generateRow: () => Seq[Parameter], 
  val required: Boolean,
  val hidden: Boolean
)(implicit owner: Ctx.Owner)
  extends Parameter
{
  def this(
    parameter: serialized.ParameterDescription, 
    children: Seq[serialized.ParameterDescriptionTree], 
    getParameter: serialized.ParameterDescriptionTree => Parameter,
    datasets: Rx[Map[String, serialized.ArtifactSummary]],
  )(implicit owner: Ctx.Owner)
  {
    this(
      parameter.id,
      parameter.name, 
      children.map { _.parameter.name },
      ListParameter.generateChildConstructors(children, datasets, getParameter),
      parameter.required,
      parameter.hidden
    )
  }

  val rows = RxBuffer[Seq[Parameter]]( tentativeRow() )
  val rowView = RxBufferView(tbody(), 
    rows.rxMap { row =>  
      tr( 
        row.map { _.root }.map { td(_) } ,
        td (
          button(
            "X",
            `class` := "delete_row",
            onclick := { e:dom.MouseEvent => 
              val idx = rows.indexOf(row)
              if(idx < rows.length - 1 && idx >= 0){
                rows.remove(idx)
              }
            }
          )
        )
      )
    })
  def lastRow = Var(rows.last)

  def tentativeRow(): Seq[Parameter] =
  {
    val row = generateRow()
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
        `class` := "parameter_list",
        thead(
          tr(
            titles.map { th(_) },
            th("")
          )
        ),
        rowView.root,
      )
    )

  def rawValue =
    rows.toSeq
        .dropRight(1) // drop the "xColMaxlate" row
        .map { row => 
          JsArray(row.map { field => Json.toJson(field.toArgument) })
        }

  def value = 
    JsArray(rawValue)

  def set(v: JsValue): Unit = 
  {
    set(
      v.as[Seq[serialized.CommandArgumentList.T]].map { 
        serialized.CommandArgumentList.toMap(_)
      }
    )
  }

  def set(v: Seq[Map[String, JsValue]]) =
  {
    rows.clear()
    for(rowData <- v){
      val row = tentativeRow()
      for(field <- row){
        field.set(rowData.getOrElse(field.id, JsNull))
      }
      rows.append(row)
    }
    rows.append(tentativeRow())
  }
}
object ListParameter
{
  def generateChildConstructors(
    children: Seq[serialized.ParameterDescriptionTree], 
    datasets: Rx[Map[String,serialized.ArtifactSummary]],
    getParameter: serialized.ParameterDescriptionTree => Parameter
  )(implicit owner: Ctx.Owner): () => Seq[Parameter] =
  {
    var datasetParameter = children.indexWhere { _.parameter.datatype == "dataset" }

    if(datasetParameter < 0){
      return { () => children.map { getParameter(_) } }
    } else {
      return { () => 
        println("Allocating row with dataset parameter")
        val dataset = getParameter(children(datasetParameter)).asInstanceOf[ArtifactParameter]

        dataset.onChange { ds =>
          println(s"Dataset changed to $ds")
        }

        children.zipWithIndex.map { 
          case (_, idx) if idx == datasetParameter => dataset
          case (x, _) if x.parameter.datatype == "colid" =>
            new ColIdParameter(
              x.parameter,
              datasets,
              dataset.selectedDataset
            )
          case (x, _) if x.parameter.datatype == "list" =>
            new ListParameter(
              x.parameter,
              x.children,
              getParameter,
              datasets
            )
          case (x, _) if x.parameter.datatype == "numericalfilter" =>
            new NumericalFilterParameter(
              x.parameter,
            )
          case (x, _) => getParameter(x)
        }
      }
    }
  }
}

/////////////////////////////////////////////////////////////////////////////
/**
 * A nested list of ColIDParameters.  
 * 
 * The elements field defines the parameters that appear in each row of the list.
 */

class NumericalFilterParameter(
    val id: String,
    val name: String,
    val profile_data: Var[Option[serialized.PropertyList.T]],
    val xDataColumn: Var[Option[Int]] = Var[Option[Int]](None),
    schema : Rx[Int],
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner) 
  extends Parameter
  {
    
  def this (
    parameter: serialized.ParameterDescription, 
  )(implicit owner: Ctx.Owner) = 
  {
    this(
      parameter.id,
      parameter.name,
      Var[Option[serialized.PropertyList.T]](None),
      Var[Option[Int]](None),
      Var[Int](0),
      parameter.required,
      parameter.hidden
    )
  }
  val currentColumn = Var[Option[String]](None)
  val xColMax = Var[Option[Int]](None)
  // val slider = dom.document.getElementsByName("Filter").asInstanceOf[dom.html.Input]
  val currentFilterValue = Var[Option[Int]](None)


  def updateXColumnData(xCol:Option[Int]): Unit = 
  {
    try { 
      profile_data.now match {
        case Some(profile_data) =>
          xCol match {
            case None => 
              println("No x column")
            case Some(curr_xCol) =>
            val columns = profile_data(2).value
            val overall_data = columns(curr_xCol).as[JsObject]
            val generalInfo = (overall_data \ "column")
            val name_filter = (generalInfo \ "name").as[String]
            val dataType = (generalInfo \ "type").as[String]
            if (dataType == "string") {
              println("X column is not numerical")
              return 
            }
            else
              {
                val maxValue = (overall_data \ "max").as[Int]
                xDataColumn() = Some(maxValue)
                xColMax() = Some(maxValue)
                currentFilterValue() = Some(maxValue)
                currentColumn() = Some(name_filter)
            }
          }
        case None => 
          println("No x column")
        }
    }
    catch {
      case e: Exception => 
        println("Error in updating x column data")
        println(e)
    }
  }

  val slider_input = Rx {
    xColMax().map { maxVal =>
        input(
            scalatags.JsDom.all.name := "slider_param",
              scalatags.JsDom.all.id := "slider_param",
              `type` := "range",
              min := 0,
              max := maxVal.toString,
              scalatags.JsDom.all.value := currentFilterValue().toString,
              onchange := { (e:dom.Event) => 
                currentFilterValue() = Some(inputNode[dom.html.Input].value.toInt)
                println("Slider value updated")
                print(currentFilterValue()) 
          }
        ).render
    }
  }

    //scalatags.JsDom.all.
  val root =  
      span(
        Rx {
          currentFilterValue() match {
            case Some(filterValue) => 
              div(
                `class` := "numerical_filter",
                  slider_input.reactive,
                  input(
                  scalatags.JsDom.all.name := "input_box",
                  `type` := "number",
                  scalatags.JsDom.all.value:= filterValue.toString),
                  onkeypress := { (e: dom.KeyboardEvent) =>
                    if (e.keyCode == 13) {
                      currentFilterValue() = Some(inputNode[dom.html.Input].value.toInt)
                  }
                }
              )
            case None => 
              div(
                `class` := "spinner_class",
                Spinner().render,
                println("No x")
              )
            case _ => 
              div(
                `class` := "spinner_class",
                Spinner().render,
              )
            }
          }.reactive
      ).render
    


  def value =
    if (xColMax.now == None) {
      JsString("")
    } else {
      JsString(currentColumn.now.get + " <= " + (inputNode[dom.html.Input].value).toString)
    }
    
  def set(v: JsValue): Unit ={
    val stringVal = v.as[String]
    if (stringVal.equals("")) {
      return 
    }
    val data = stringVal.split(" <= ")
    println(data)
    xColMax() = Some(data(1).toInt)
    currentColumn() = Some(data(0))
    currentFilterValue() = Some(data(1).toInt)
  }
    
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
    Json.toJson(elements.map { _.toArgument })
  def set(v: JsValue): Unit = 
  {
    val data = serialized.CommandArgumentList.decodeAsMap(v)
    for(field <- elements){
      field.set( data.getOrElse(field.id, JsNull) )
    }
  }
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
    JsString(inputNode[dom.html.Input].value)
  def set(v: JsValue): Unit = 
    inputNode[dom.html.Input].value = v.as[String]

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
    JsString(inputNode[dom.html.Select].value)
  def set(v: JsValue): Unit = 
    if(v != JsNull){
      inputNode[dom.html.Select].value = v.as[String]
    }

}

/////////////////////////////////////////////////////////////////////////////

/**
 * A string-valued parameter
 */
class StringParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean,
  val initialPlaceholder: String = "",
  val initialPlaceholderIsDefaultValue: Boolean = true
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden,
      initialPlaceholder = parameter.helpText.getOrElse(""),
      initialPlaceholderIsDefaultValue = !parameter.helpText.isDefined
    )
  }
  val root = 
    input(`type` := "text", placeholder := initialPlaceholder).render.asInstanceOf[dom.html.Input]
  def value =
    JsString(
      inputNode[dom.html.Input].value match {
        case "" if initialPlaceholderIsDefaultValue => 
          inputNode[dom.html.Input].placeholder
        case x => x
      }
    )
  def set(v: JsValue): Unit = 
    inputNode[dom.html.Input].value = v match {
      case JsString(s) => s
      case _ => 
        println(s"WARNING: String parameter $name ($id) is being set to non-string value $v")
        v.toString
    }
  def setHint(s: String): Unit =
    inputNode[dom.html.Input].placeholder = s
}

/**
 * A string-valued parameter
 */
class JsonParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean,
  val initialPlaceholder: String = ""
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
    input(`type` := "text", placeholder := initialPlaceholder).render.asInstanceOf[dom.html.Input]
  def value =
    Json.parse(inputNode[dom.html.Input].value)
  def set(v: JsValue): Unit = 
    inputNode[dom.html.Input].value = v.toString
  def setHint(s: String): Unit =
    inputNode[dom.html.Input].placeholder = s
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter with a limited set of options
 */
class DataTypeParameter(
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
    pulldown(
      DataTypes.BY_NAME.indexWhere { _._2 == "int" }
    )(DataTypes.BY_NAME:_*)
      .render.asInstanceOf[dom.html.Select]

  var unexpected: Option[JsValue] = None

  def value = 
    unexpected.getOrElse {
      JsString(inputNode[dom.html.Select].value)
    }
  def set(v: JsValue): Unit = 
    v match {
      case JsString(s) => inputNode[dom.html.Select].value = s
      case _ => unexpected = Some(v)
    }
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A parameter with a limited set of options
 */
class EnvironmentParameter(
  val id: String, 
  val name: String, 
  val language: String,
  val required: Boolean,
  val hidden: Boolean
)(implicit owner: Ctx.Owner) extends Parameter
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  def this(parameter: serialized.CodeParameterDescription)(implicit owner: Ctx.Owner)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      language = parameter.language,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }

  val options = Var[Seq[serialized.PythonEnvironmentSummary]](Seq.empty)
  val selected = Var[Option[serialized.PythonEnvironmentSummary]](None)

  Vizier.api.pythonEnvironments.get.onSuccess { 
    case envs => 
      options() = envs 
      // println(
      //   options.now.map { _.name }.mkString("\n")
      // )
  }

  def systemEnvironment: serialized.PythonEnvironmentSummary =
    options.now.find { _.name == "System" }
               .getOrElse { Vizier.error("No System Environment") }

  override def value: JsValue = 
  {
    Json.toJson(
      selected.now.getOrElse { systemEnvironment }
    )
  }

  override def set(v: JsValue): Unit = 
  {
    v match {
      case JsNull => selected() = None
      case _ => selected() = Some(v.as[serialized.PythonEnvironmentSummary])
    }
  }

  def updateRevision(): Unit =
  {
    val myId = selected.now.get.id
    val rev = options.now.find { _.id == myId  }
    selected() = rev
  }

  val identity = s"parameter_${Parameter.nextInputId}"

  override val root: Node = 
    Rx {
      val selectedId = selected().map { _.id }.getOrElse(-1)
      div(`class` := "environment_selector",
        label(
          `for` := identity,
          b("Python Version: ")
        ),
        select(
          attr("id") := identity,
          options().map { env =>
            if(selectedId == env.id){ 
              option(attr("value") := env.id, s"${env.name} (Python ${env.pythonVersion})", attr("selected") := "yes")
            } else {
              option(attr("value") := env.id, s"${env.name} (Python ${env.pythonVersion})")
            }
          },
          onchange := { e:dom.Event => 
            val v = e.target.asInstanceOf[dom.html.Select].value.toLong
            selected() = options.now.find { _.id == v }
          }
        ),
        selected() match {
          case None => span(
            `class` := "environment_warning",
            "No python environment selected, the non-reproducible system python will be used."
          )
          case Some(env) => 
            val base = options().find { _.id == env.id }
            if(base.isEmpty){
              span(
                `class` := "environment_warning",
                "The selected Python environment has been deleted; You must select an environment to use."
              )
            } else if(base.get.revision != env.revision) {
              span(
                `class` := "environment_warning",
                "The selected Python environment has been modified since this cell was last run",
                button(
                  "Acknowledge",
                  onclick := { _:dom.Event => updateRevision() }
                ),
                button(
                  "Fork",
                  onclick := { _:dom.Event => Vizier.error("Forking not supported yet") }
                )
              )
            } else {
              span()
            }

        }
      )
    }.reactive
}

/////////////////////////////////////////////////////////////////////////////

/**
 * A fallback class to use a parameter of an unknown type
 */
class UnsupportedParameter(
  val id: String, 
  val name: String, 
  dataType: String,
  context: String,
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
      parameter.getClass().getSimpleName(),
      parameter.required, 
      parameter.hidden
    )
  }
  val root = span(s"Unsupported parameter type: $dataType ($context)")
  def value = JsNull
  def set(v: JsValue): Unit = {}
}

class ColorParameter(
  val id: String, 
  val name: String, 
  val required: Boolean,
  val hidden: Boolean,
  val defaultColor: Option[String]
) extends Parameter
{
  def this(parameter: serialized.ParameterDescription)
  {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden,
      defaultColor = None
    )
  }

  def hasDefaultColor: Boolean = defaultColor.isDefined

  def getDefaultColor: String = defaultColor.getOrElse("#FFFFFF")


  val root = 
    div(
      `class` := "label_parameter",
      (1 to 6).map { i =>
          input(
            `type` := "radio",
            scalatags.JsDom.all.name := "radioButton",
            scalatags.JsDom.all.value := s"Option $i"
          )
      }
    ).render



  def value = JsNull
  def set(v: JsValue): Unit = {}
}
