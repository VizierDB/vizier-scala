/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
import info.vizierdb.ui.facades.{CodeMirror, CodeMirrorEditor}
import info.vizierdb.ui.rxExtras.{OnMount, RxBuffer, RxBufferView}
import info.vizierdb.util.{Logger, Logging}
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.ui.components.snippets.PythonSnippets
import info.vizierdb.ui.components.snippets.SnippetsBase
import info.vizierdb.ui.components.snippets.ScalaSnippets
import org.scalajs.dom.Node
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner

class ParameterError(msg: String, val parameter: Parameter)
    extends Exception(msg)

/** A parameter for a command. Primarily used by [[ModuleEditor]]
  */
sealed trait Parameter {

  /** A unique identifier for the parameter; the key in the module arguments
    */
  val id: String

  /** A human-readable name for the parameter.
    */
  val name: String

  /** If true, the parameter value must be set before the form is submitted
    */
  val required: Boolean

  /** If true, the value is hidden. Hidden arguments are used to pass state
    * through different iterations of the workflow, and/or backwards
    * compatibility.
    */
  val hidden: Boolean

  /** The DOM [[Node]] used to display the parameter's input widget
    */
  val root: dom.Node

  /** The current value of this parameter's input widget
    */
  def value: JsValue

  /** Update the current value of this parameter's input widget
    */
  def set(v: JsValue)

  /** Encode the parameter and its value as a [[ModuleArgument]]
    */
  def toArgument: serialized.CommandArgument =
    serialized.CommandArgument(id, value)

  /** Callbacks to trigger when the value of the element changes
    */
  private val changeHandlers = mutable.Buffer[dom.Event => Unit]()

  /** Register code to run when the element's value changes
    */
  def onChange(handler: dom.Event => Unit) =
    changeHandlers.append(handler)

  /** Generic utility constructor for DOM [[Node]]s for the parameter's field.
    */
  def field(
      basetag: String,
      attrs: AttrPair*
  )(elems: Frag*): Frag = {
    val identity = s"parameter_${Parameter.nextInputId}"
    div(
      `class` := "parameter",
      label(attr("for") := identity, name),
      tag(basetag)(
        attrs,
        `class` := Parameter.PARAMETER_WIDGET_CLASS,
        attr("id") := identity,
        attr("name") := name,
        elems
      ),
      onchange := { (e: dom.Event) => changeHandlers.foreach { _(e) } }
    )
  }

  /** &lt;input&gt;-tag utility constructor for the parameter's field
    */
  def input(
      attrs: AttrPair*
  ): Frag = field("input", attrs: _*)()

  /** &lt;select&gt;-tag utility constructor for the parameter's field
    */
  def pulldown(
      selected: Int
  )(options: (String, String)*): Frag =
    field("select")(
      options.zipWithIndex.map { case ((description, value), idx) =>
        option(
          attr("value") := value,
          if (idx == selected) { attr("selected", raw = true) := "" }
          else { "" },
          description
        )
      }: _*
    )

  def inputNode[T <: dom.Node]: T =
    findArgumentNode(root).get.asInstanceOf[T]

  def findArgumentNode(search: dom.Node): Option[dom.Node] = {
    if (search.attributes.equals(js.undefined)) { return None }
    val classAttr =
      search.attributes.getNamedItem("class")
    val isCommand =
      Option(classAttr)
        .map { _.value.split(" ") contains Parameter.PARAMETER_WIDGET_CLASS }
        .getOrElse { false }
    if (isCommand) {
      return Some(search)
    } else {
      for (i <- 0 until search.childNodes.length) {
        val r = findArgumentNode(search.childNodes(i))
        if (r.isDefined) { return r }
      }
      return None
    }
  }
}

/** Utility methods for decoding [[Parameter]] instances
  */
object Parameter extends Logging {
  val PARAMETER_WIDGET_CLASS = "command-argument"

  /** Decode a [[ParameterDescriptor]] into a [[Parameter]] for use with the
    * specified [[ModuleEditor]]
    */
  def apply(
      tree: serialized.ParameterDescriptionTree,
      editor: DefaultModuleEditor
  )(implicit owner: Ctx.Owner): Parameter = {
    def visibleArtifactsByType = editor.delegate.visibleArtifacts
      .map { _.mapValues { _._1.t } }
    def visibleArtifacts = editor.delegate.visibleArtifacts
      .map { _.mapValues { _._1 } }

    tree.parameter match {
      case param: serialized.SimpleParameterDescription =>
        param.datatype match {
          case "colid" =>
            new ColIdParameter(param, visibleArtifacts, editor.selectedDataset)
          case "list" =>
            new ListParameter(
              param,
              tree.children,
              this.apply(_, editor),
              visibleArtifacts
            )
          case "numericalfilter" => new NumericalFilterParameter(param)
          case "color"           => new ColorParameter(param)
          case "record"          => new RecordParameter(param, tree.children, this.apply(_, editor))
          case "string"  => new StringParameter(param)
          case "int"     => new IntParameter(param)
          case "decimal" => new DecimalParameter(param)
          case "bool"    => new BooleanParameter(param)
          case "rowid"   => new RowIdParameter(param)
          case "fileid"  => new FileParameter(param)
          case "multi" => new MultiSelectParameter(param, visibleArtifacts, editor.selectedDataset)
          case "dataset" =>
            new ArtifactParameter(
              param,
              ArtifactType.DATASET,
              visibleArtifactsByType
            )
          case "datatype" => new DataTypeParameter(param)
          case "json"     => new JsonParameter(param)
          case _          => new UnsupportedParameter(param)
        }

      case param: serialized.CodeParameterDescription =>
        param.datatype match {
          case "environment" => new EnvironmentParameter(param)
          case _             => new CodeParameter(param)
        }

      case param: serialized.ArtifactParameterDescription =>
        new ArtifactParameter(param, visibleArtifactsByType)

      case param: serialized.EnumerableParameterDescription =>
        new EnumerableParameter(param)

      case _ => new UnsupportedParameter(tree.parameter)
    }
  }

  private var nextInputIdValue: Long = -1L

  /** Each parameter has a unique identifier; Allocate a fresh one
    */
  def nextInputId = { nextInputIdValue += 1; nextInputIdValue }
}

/////////////////////////////////////////////////////////////////////////////

/** A Boolean-valued parameter
  */
class BooleanParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {

  def this(
      id: String,
      name: String,
      required: Boolean,
      hidden: Boolean,
      default: Boolean
  ) {
    this(id, name, required, hidden)
    set(JsBoolean(default))
  }

  def this(parameter: serialized.ParameterDescription) {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root =
    input(`type` := "checkbox").render
  def value = {
    JsBoolean(inputNode[dom.html.Input].checked)
  }

  override def set(v: JsValue): Unit =
    inputNode[dom.html.Input].checked = v.as[Boolean]
}

/////////////////////////////////////////////////////////////////////////////

/** A parameter that accepts a block of code. Implemented with CodeMirror
  */
case class CodeParameter(
    val id: String,
    val name: String,
    language: String,
    val required: Boolean,
    val hidden: Boolean,
    val startWithSnippetsHidden: Boolean = false
)(implicit owner: Ctx.Owner)
    extends Parameter {

  def this(
      parameter: serialized.CodeParameterDescription
  )(implicit owner: Ctx.Owner) {
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
      CodeParameter.SNIPPETS.get(language).map {
        _.apply { snippet =>
          editor.replaceSelection("\n" + snippet)
        }
      }
    )

  val root =
    div(
      width := "100%",
      textarea(
        // "code goes here...",
        OnMount { (n: dom.Node) =>
          editor = CodeMirror.fromTextArea(
            n,
            js.Dictionary(
              "mode" -> CodeParameter.CODEMIRROR_FORMAT
                .getOrElse(language, "text/plain"),
              "lineNumbers" -> true,
              "viewportMargin" -> Double.PositiveInfinity
            )
          )
          if (initialValue != null) { editor.setValue(initialValue) }
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
      Option(editor)
        .map { _.getValue }
        .getOrElse { "" }
    )

  def set(v: String): Unit = {
    initialValue = v
    if (editor != null) { editor.setValue(v) }
  }

  override def set(v: JsValue): Unit =
    set(v.as[String])
}
object CodeParameter {

  /** Translation table from Vizier-native format descriptions to CodeMirror's
    * identifier
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

/** A parameter to select a column from the currently selected dataset.
  *
  * A reactive list of [[DatasteColumn]]s determines the list of columns shown.
  * This is typically derived from the first [[DatsetParameter]] in the
  * parameter list, and the datasets visible at this point in the workflow.
  */
class ColIdParameter(
    val id: String,
    val name: String,
    schema: Rx[Seq[serialized.DatasetColumn]],
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {

  def this(
      parameter: serialized.ParameterDescription,
      datasets: Rx[Map[String, serialized.ArtifactSummary]],
      selectedDataset: Rx[Option[String]]
  )(implicit owner: Ctx.Owner) {
    this(
      parameter.id,
      parameter.name,
      Rx {
        selectedDataset() match {
          case None => Seq.empty
          case Some(dsName) =>
            datasets().get(dsName) match {
              case None =>
                Parameter.logger.warn(
                  s"ColIdParameter $name used with an undefined artifact"
                )
                Seq.empty
              case Some(ds: serialized.DatasetSummary)     => ds.columns
              case Some(ds: serialized.DatasetDescription) => ds.columns
              case Some(_) =>
                Parameter.logger.warn(
                  s"ColIdParameter $name used with a non-dataset artifact"
                )
                Seq.empty
            }
        }
      },
      parameter.required,
      parameter.hidden
    )
  }

  val selectedColumn = Var[Option[Int]](None)

  onChange { e: dom.Event =>
    selectedColumn() = inputNode[dom.html.Select].value match {
      case "" => None
      case x  => Some(x.toInt)
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
        ): _*
      )
    }.reactive
  )
  def value =
    JsNumber(inputNode[dom.html.Select].value.toInt)
  override def set(v: JsValue): Unit = {
    selectedColumn() = v.asOpt[Int]
  }

}

/////////////////////////////////////////////////////////////////////////////

/** A parameter to select an artifact.
  *
  * A reactive list of artifacts must be provided to the parameter. This is
  * typically derived from the list of artifacts visible at the point in the
  * workflow where the module is being inserted.
  */
class ArtifactParameter(
    val id: String,
    val name: String,
    val artifactType: ArtifactType.T,
    artifacts: Rx[Map[String, ArtifactType.T]],
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {

  def this(
      parameter: serialized.ArtifactParameterDescription,
      artifacts: Rx[Map[String, ArtifactType.T]]
  )(implicit owner: Ctx.Owner) {
    this(
      parameter.id,
      parameter.name,
      parameter.artifactType,
      artifacts,
      parameter.required,
      parameter.hidden
    )
  }
  def this(
      parameter: serialized.SimpleParameterDescription,
      artifactType: ArtifactType.T,
      artifacts: Rx[Map[String, ArtifactType.T]]
  )(implicit owner: Ctx.Owner) {
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

  onChange { e: dom.Event =>
    selectedDataset() = e
      .asInstanceOf[js.Dynamic]
      .target
      .value
      .asInstanceOf[String] match {
      case "" => None
      case x  => Some(x)
    }
  }

  val root = span(
    Rx {
      pulldown(0)(
        (
          Seq("---" -> "") ++
            artifacts()
              .filter { _._2 == artifactType }
              .map { x => x._1 -> x._1 }
        ): _*
      )
    }.reactive
  )
  def value =
    inputNode[dom.html.Select].value match {
      case "" => JsNull
      case x  => JsString(x)
    }
  override def set(v: JsValue): Unit = {
    selectedDataset() = v.asOpt[String]
    inputNode[dom.html.Select].value = v.asOpt[String].getOrElse { "" }
  }
}

/////////////////////////////////////////////////////////////////////////////

/** A Decimal-valued parameter
  */
class DecimalParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root =
    input(`type` := "number", step := "0.01").render
      .asInstanceOf[dom.html.Input]
  def value =
    JsNumber(inputNode[dom.html.Input].value.toDouble)
  override def set(v: JsValue): Unit =
    inputNode[dom.html.Input].value = v.as[Float].toString
}

/////////////////////////////////////////////////////////////////////////////

/** A file selector
  */
class FileParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {
  def this(
      parameter: serialized.ParameterDescription
  )(implicit owner: Ctx.Owner) {
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

  val dragAndDropField: dom.Node =
    div(
      `class` := "file-drop-area",
      bodyText.reactive,
      ondrop := { (e: dom.DragEvent) =>
        bodyText() = span("file dropped")
        e.preventDefault()
      },
      ondragover := { (e: dom.DragEvent) =>
        bodyText() = span("file in drop zone")
        e.preventDefault()
      },
      ondragleave := { (e: dom.DragEvent) =>
        bodyText() = span(DEFAULT_BODY_TEXT)
        e.preventDefault()
      }
    )
  val urlField: dom.Node = {
    val identity = s"parameter_${Parameter.nextInputId}"
    div(
      `class` := "parameter",
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
      if (mode().equals(idx)) {
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
      case 0 =>
        Json.obj("fileid" -> uploadedFileId, "filename" -> uploadedFileName)
      case 1 => Json.obj("url" -> inputNode[dom.html.Input].value)
      case _ => JsNull
    }
  def set(v: JsValue): Unit = {
    val url = (v \ "url").asOpt[String]
    val fileid = (v \ "fileid").asOpt[Identifier]
    val filename = (v \ "filename").asOpt[String]

    if (url.isDefined) {
      mode() = 1
      inputNode[dom.html.Input].value = url.get
    } else if (fileid.isDefined) {
      mode() = 0
      uploadedFileId = fileid.get
      uploadedFileName = filename.getOrElse { "uploaded-file" }
    } else {
      println(
        "Received invalid file parameter... leaving the upload space blank."
      )
    }
  }
}

/////////////////////////////////////////////////////////////////////////////

/** An Integer-valued parameter
  */
class IntParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
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

/** A nested list of parameters.
  *
  * The elements field defines the parameters that appear in each row of the
  * list.
  */
class ListParameter(
    val id: String,
    val name: String,
    titles: Seq[String],
    generateRow: () => Seq[Parameter],
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {
  def this(
      parameter: serialized.ParameterDescription,
      children: Seq[serialized.ParameterDescriptionTree],
      getParameter: serialized.ParameterDescriptionTree => Parameter,
      datasets: Rx[Map[String, serialized.ArtifactSummary]]
  )(implicit owner: Ctx.Owner) {
    this(
      parameter.id,
      parameter.name,
      children.map { _.parameter.name },
      ListParameter.generateChildConstructors(children, datasets, getParameter),
      parameter.required,
      parameter.hidden
    )
  }

  val rows = RxBuffer[Seq[Parameter]](tentativeRow())
  val rowView = RxBufferView(
    tbody(),
    rows.rxMap { row =>
      tr(
        row.map { _.root }.map { td(_) },
        td(
          button(
            "X",
            `class` := "delete_row",
            onclick := { e: dom.MouseEvent =>
              val idx = rows.indexOf(row)
              if (idx < rows.length - 1 && idx >= 0) {
                rows.remove(idx)
              }
            }
          )
        )
      )
    }
  )
  def lastRow = Var(rows.last)

  def tentativeRow(): Seq[Parameter] = {
    val row = generateRow()
    row.foreach { _.onChange { e => touchRow(row) } }
    row
  }

  def touchRow(row: Seq[Parameter]) {
    if (row == lastRow.now) {
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
        rowView.root
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

  def set(v: JsValue): Unit = {
    set(
      v.as[Seq[serialized.CommandArgumentList.T]].map {
        serialized.CommandArgumentList.toMap(_)
      }
    )
  }

  def set(v: Seq[Map[String, JsValue]]) = {
    rows.clear()
    for (rowData <- v) {
      val row = tentativeRow()
      for (field <- row) {
        field.set(rowData.getOrElse(field.id, JsNull))
      }
      rows.append(row)
    }
    rows.append(tentativeRow())
  }
}
object ListParameter {
  def generateChildConstructors(
      children: Seq[serialized.ParameterDescriptionTree],
      datasets: Rx[Map[String, serialized.ArtifactSummary]],
      getParameter: serialized.ParameterDescriptionTree => Parameter
  )(implicit owner: Ctx.Owner): () => Seq[Parameter] = {
    var datasetParameter = children.indexWhere {
      _.parameter.datatype == "dataset"
    }

    if (datasetParameter < 0) {
      return { () => children.map { getParameter(_) } }
    } else {
      return { () =>
        println("Allocating row with dataset parameter")
        val dataset = getParameter(children(datasetParameter))
          .asInstanceOf[ArtifactParameter]

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
          case (x, _) if x.parameter.datatype == "color" =>
            new ColorParameter(x.parameter)
          case (x, _) if x.parameter.datatype == "list" =>
            new ListParameter(
              x.parameter,
              x.children,
              getParameter,
              datasets
            )
          case (x, _) if x.parameter.datatype == "numericalfilter" =>
            new NumericalFilterParameter(
              x.parameter
            )
          case (x, _) => getParameter(x)
        }
      }
    }
  }
}


class NumericalFilterParameter(
    val id: String,
    val name: String,
    val profiler_dump: Var[Option[serialized.PropertyList.T]],
    schema: Rx[Int],
    columns: Rx[Seq[serialized.DatasetColumn]],
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {

  def this(
      parameter: serialized.ParameterDescription
  )(implicit owner: Ctx.Owner) = {
    this(
      parameter.id,
      parameter.name,
      Var[Option[serialized.PropertyList.T]](None),
      Var[Int](0),
      Var[Seq[serialized.DatasetColumn]](Seq.empty),
      parameter.required,
      parameter.hidden
    )
  }
  val columnName = Var[Option[String]](None)
  val maxFilterValue = Var[Option[Int]](None)
  val currentFilterValue = Var[Option[Int]](None)
  val profiledComplete = Var[Boolean](false)
  val temp = Var[Boolean](false)
  val selectedFilterValue = Var[Option[Int]](None)

  Rx {
    profiler_dump().map {
      profiler_dump =>
        val columns = profiler_dump(2).value
        val overall_data = columns(schema.now).as[JsObject]
        val generalInfo = (overall_data \ "column")
        val name_filter = (generalInfo \ "name").as[String]
        val dataType = (generalInfo \ "type").as[String]
        if (dataType == "string") {
          println("X column is not numerical")
          maxFilterValue() = None
          currentFilterValue() = None
          columnName() = None
          profiledComplete() = false
          temp() = true
        } else {
          try {
            val maxValue = (overall_data \ "max").as[Int]
            maxFilterValue() = Some(maxValue)
            currentFilterValue() = Some(maxValue)
            columnName() = Some(name_filter)
            profiledComplete() = true
          } catch {
            case e: Exception =>
              println("Error in updating x column data")
              println(e)
          }
        }
    }
  }

  onChange { e: dom.Event =>
    selectedFilterValue() = inputNode[dom.html.Select].value match {
      case "" => None
      case x  => Some(x.toInt)
    }
  }

  Rx{
    selectedFilterValue().foreach { filterValue =>{
      println("Schema updated")
      profiler_dump.now.map { profiler_dump =>
        val columns = profiler_dump(2).value
        val overall_data = columns(filterValue).as[JsObject]
        val generalInfo = (overall_data \ "column")
        val name_filter = (generalInfo \ "name").as[String]
        val dataType = (generalInfo \ "type").as[String]
        if (dataType == "string") {
          println("X column is not numerical")
          maxFilterValue() = None
          currentFilterValue() = None
          columnName() = None
          profiledComplete() = false
          temp() = true
        } else {
          try {
            val maxValue = (overall_data \ "max").as[Int]
            println("Max value is " + maxValue)
            maxFilterValue() = Some(maxValue)
            currentFilterValue() = Some(maxValue)
            columnName() = Some(name_filter)
            profiledComplete() = true
          } catch {
            case e: Exception =>
              println("Error in updating x column data")
              println(e)
            }
          }
        }
      }
    }
}

val pulldownColumns = Rx {
  val options = ("---", "none") +: columns().map { v => (v.name, v.id.toString) }
  pulldown(
    columns().zipWithIndex.find(_._1 == columnName.now.getOrElse("")).map(_._2 + 1).getOrElse(0)
  )(options: _*).render.asInstanceOf[dom.html.Select]
}

  val sliderInput = 
    input(
    `type` := "range",
    min := 0,
    max := maxFilterValue.now).render.asInstanceOf[dom.html.Input]

    sliderInput.style.maxWidth = 120.px
    sliderInput.style.minWidth = 90.px
    sliderInput.style.verticalAlign = "center"


  val stringInput = input(
    `type` := "text",
    placeholder := "Enter Filter"
  )

  val root =
    span(
      Rx {
        profiledComplete() match {
          case true =>
            div(
              `class` := "numerical_filter",
              pulldownColumns.reactive,
              sliderInput,
            )
          case false =>
            temp() match {
              case true =>
                div(
                  `class` := "numerical_filter",
                  pulldownColumns.reactive,
                  stringInput.render,
                  println("Yes x")
                )
              case false =>
                div(
                  `class` := "spinner_class",
                  Spinner().render,
                  println("No x")
              )
            }
        }
      }.reactive
    )

  def value = {
    if(inputNode[dom.html.Input].value == "none") {
      JsString("")
    }
    else if (maxFilterValue.now == None) {
      JsString("")
    } 
    else if (temp.now == true) {
      JsString(inputNode[dom.html.Input].value)
    }
    else {
      println("DSADSA")
      JsString(
        columnName.now.get + " <= " + (inputNode[
          dom.html.Input
        ].value).toString
      )
    }
  }
  def set(v: JsValue): Unit = {
    val stringVal = v.as[String]
    if (stringVal.equals("")) {
      return
    }
    val data = stringVal.split(" <= ")
    println(data)
    maxFilterValue() = Some(data(1).toInt)
    columnName() = Some(data(0))
    currentFilterValue() = Some(data(1).toInt)
  }

}

/////////////////////////////////////////////////////////////////////////////

/** A group of parameters.
  *
  * The elements field contains the parameters in the group.
  */
class RecordParameter(
    val id: String,
    val name: String,
    elements: Seq[Parameter],
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(
      parameter: serialized.ParameterDescription,
      children: Seq[serialized.ParameterDescriptionTree],
      getParameter: serialized.ParameterDescriptionTree => Parameter
  )(implicit owner: Ctx.Owner) {
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
  def set(v: JsValue): Unit = {
    val data = serialized.CommandArgumentList.decodeAsMap(v)
    for (field <- elements) {
      field.set(data.getOrElse(field.id, JsNull))
    }
  }
}

/////////////////////////////////////////////////////////////////////////////

/** A parameter indicating a specific row
  */
class RowIdParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
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

/** A parameter with a limited set of options
  */
class EnumerableParameter(
    val id: String,
    val name: String,
    values: Seq[serialized.EnumerableValueDescription],
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.EnumerableParameterDescription) {
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
    )(values.map { v => v.text -> v.value }: _*).render
      .asInstanceOf[dom.html.Select]

  def value =
    JsString(inputNode[dom.html.Select].value)
  def set(v: JsValue): Unit =
    if (v != JsNull) {
      inputNode[dom.html.Select].value = v.as[String]
    }

}

/////////////////////////////////////////////////////////////////////////////

/** A string-valued parameter
  */
class StringParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean,
    val initialPlaceholder: String = "",
    val initialPlaceholderIsDefaultValue: Boolean = true
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
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
    input(`type` := "text", placeholder := initialPlaceholder).render
      .asInstanceOf[dom.html.Input]
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
        println(
          s"WARNING: String parameter $name ($id) is being set to non-string value $v"
        )
        v.toString
    }
  def setHint(s: String): Unit =
    inputNode[dom.html.Input].placeholder = s
}

/** A string-valued parameter
  */
class JsonParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean,
    val initialPlaceholder: String = ""
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden
    )
  }
  val root =
    input(`type` := "text", placeholder := initialPlaceholder).render
      .asInstanceOf[dom.html.Input]
  def value =
    Json.parse(inputNode[dom.html.Input].value)
  def set(v: JsValue): Unit =
    inputNode[dom.html.Input].value = v.toString
  def setHint(s: String): Unit =
    inputNode[dom.html.Input].placeholder = s
}

/////////////////////////////////////////////////////////////////////////////

/** A parameter with a limited set of options
  */
class DataTypeParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
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
    )(DataTypes.BY_NAME: _*).render.asInstanceOf[dom.html.Select]

  var unexpected: Option[JsValue] = None

  def value =
    unexpected.getOrElse {
      JsString(inputNode[dom.html.Select].value)
    }
  def set(v: JsValue): Unit =
    v match {
      case JsString(s) => inputNode[dom.html.Select].value = s
      case _           => unexpected = Some(v)
    }
}

/////////////////////////////////////////////////////////////////////////////

/** A parameter with a limited set of options
  */
class EnvironmentParameter(
    val id: String,
    val name: String,
    val language: String,
    val required: Boolean,
    val hidden: Boolean
)(implicit owner: Ctx.Owner)
    extends Parameter {
  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def this(
      parameter: serialized.CodeParameterDescription
  )(implicit owner: Ctx.Owner) {
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

  Vizier.api.pythonEnvironments.get.onSuccess { case envs =>
    options() = envs
  // println(
  //   options.now.map { _.name }.mkString("\n")
  // )
  }

  def systemEnvironment: serialized.PythonEnvironmentSummary =
    options.now
      .find { _.name == "System" }
      .getOrElse { Vizier.error("No System Environment") }

  override def value: JsValue = {
    Json.toJson(
      selected.now.getOrElse { systemEnvironment }
    )
  }

  override def set(v: JsValue): Unit = {
    v match {
      case JsNull => selected() = None
      case _ => selected() = Some(v.as[serialized.PythonEnvironmentSummary])
    }
  }

  def updateRevision(): Unit = {
    val myId = selected.now.get.id
    val rev = options.now.find { _.id == myId }
    selected() = rev
  }

  val identity = s"parameter_${Parameter.nextInputId}"

  override val root: Node =
    Rx {
      val selectedId = selected().map { _.id }.getOrElse(-1)
      div(
        `class` := "environment_selector",
        label(
          `for` := identity,
          b("Python Version: ")
        ),
        select(
          attr("id") := identity,
          options().map { env =>
            if (selectedId == env.id) {
              option(
                attr("value") := env.id,
                s"${env.name} (Python ${env.pythonVersion})",
                attr("selected") := "yes"
              )
            } else {
              option(
                attr("value") := env.id,
                s"${env.name} (Python ${env.pythonVersion})"
              )
            }
          },
          onchange := { e: dom.Event =>
            val v = e.target.asInstanceOf[dom.html.Select].value.toLong
            selected() = options.now.find { _.id == v }
          }
        ),
        selected() match {
          case None =>
            span(
              `class` := "environment_warning",
              "No python environment selected, the non-reproducible system python will be used."
            )
          case Some(env) =>
            val base = options().find { _.id == env.id }
            if (base.isEmpty) {
              span(
                `class` := "environment_warning",
                "The selected Python environment has been deleted; You must select an environment to use."
              )
            } else if (base.get.revision != env.revision) {
              span(
                `class` := "environment_warning",
                "The selected Python environment has been modified since this cell was last run",
                button(
                  "Acknowledge",
                  onclick := { _: dom.Event => updateRevision() }
                ),
                button(
                  "Fork",
                  onclick := { _: dom.Event =>
                    Vizier.error("Forking not supported yet")
                  }
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

/** A fallback class to use a parameter of an unknown type
  */
class UnsupportedParameter(
    val id: String,
    val name: String,
    dataType: String,
    context: String,
    val required: Boolean,
    val hidden: Boolean
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
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

/////////////////////////////////////////////////////////////////////////////

/** A parameter with a limited set of options
  */

class ColorParameter(
    val id: String,
    val name: String,
    val required: Boolean,
    val hidden: Boolean,
    val defaultColor: Option[String]
) extends Parameter {
  def this(parameter: serialized.ParameterDescription) {
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      hidden = parameter.hidden,
      defaultColor = None
    )
  }

  val selectedColor: Var[String] = Var("1") 

  val root =
    div(
      `class` := "color_parameter",
      (1 to 6).map { i =>
        label (
          span(
          input(
            `type` := "radio",
            scalatags.JsDom.all.name := "radioButton",
            scalatags.JsDom.all.value := i,
            onchange := { (e: dom.Event) =>
              selectedColor() = i.toString()
            }
          )
        )
        )
      }
    ).render

  def value = {
    println("Getting color value")
    println(selectedColor.now)
    selectedColor.now match {
      case "1" => JsString("#000000")
      case "2" => JsString("#FFFFFF")
      case "3" => JsString("#214478")
      case "4" => JsString("#FF0000")
      case "5" => JsString("#FFD600")
      case "6" => JsString("#00B507")
    }
  }
  def set(v: JsValue): Unit = {
    val color = v.as[String]
    val radio = color match {
      case "#0000FF" => "1"
      case "#FF0000" => "2"
      case "#000000" => "3"
      case "#FFFFFF" => "4"
      case "#800080" => "5"
      case "#008000" => "6"
    }
    inputNode[dom.html.Input].value = radio.toString
  }
}

class MultiSelectParameter (
    val id: String,
    val name: String,
    val required: Boolean,
    parameters: Rx[Seq[info.vizierdb.serialized.DatasetColumn]],
    val hidden: Boolean,
)(implicit owner: Ctx.Owner) extends Parameter {
  def this(
      parameter: serialized.ParameterDescription,
      datasets: Rx[Map[String, serialized.ArtifactSummary]],
      selectedDataset: Rx[Option[String]]) 
      (implicit owner: Ctx.Owner){
    this(
      id = parameter.id,
      name = parameter.name,
      required = parameter.required,
      Rx {
        selectedDataset() match {
          case None => Seq.empty
          case Some(dsName) =>
            datasets().get(dsName) match {
              case None =>
                Parameter.logger.warn(
                  s"ColIdParameter $name used with an undefined artifact"
                )
                Seq.empty
              case Some(ds: serialized.DatasetSummary)     => ds.columns
              case Some(ds: serialized.DatasetDescription) => ds.columns
              case Some(_) =>
                Parameter.logger.warn(
                  s"ColIdParameter $name used with a non-dataset artifact"
                )
                Seq.empty
            }
        }
      },
      hidden = parameter.hidden
    )
  }

  // Updated for multi-select 
  val selectedColumns = Var[Set[Int]](Set.empty) 

  // Checkbox creation function (Illustrative)
  def createCheckbox(col: info.vizierdb.serialized.DatasetColumn) = {
    val checkbox = 
      div(
        `class` := "checkbox",
        input(`type` := "checkbox", scalatags.JsDom.all.value := col.id.toString, checked := false)
      ).render.asInstanceOf[dom.html.Input]
    div(checkbox, col.name)
  }

  val root = span(
    Rx {
      div(
        parameters().map(createCheckbox)
      )
    }.reactive 
  )

  def value = {
    val selected = inputNode[dom.html.Select].value
    val selectedColumns = selected.split(",").map(_.toInt)
    JsArray(selectedColumns.map(JsNumber(_)))
  }

  def set(v: JsValue): Unit = {
    val selected = v.as[Seq[String]]
    inputNode[dom.html.Select].value = selected.head
  }
}