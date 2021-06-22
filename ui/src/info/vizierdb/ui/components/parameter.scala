package info.vizierdb.ui.components

import scala.collection.mutable
import scalajs.js
import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types.ArtifactType
import info.vizierdb.ui.network.{ ParameterDescriptor, DatasetColumn }
import info.vizierdb.ui.facades.CodeMirror
import info.vizierdb.ui.rxExtras.{ OnMount, RxBuffer, RxBufferView }

sealed trait Parameter
{
  val id: String
  val name: String
  val required: Boolean
  val hidden: Boolean
  val root: dom.Node

  val changeHandlers = mutable.Buffer[dom.Event => Unit]()
  def onChange(handler: dom.Event => Unit) =
    changeHandlers.append(handler)

  def input(
    name: String, 
    basetag: String = "input"
  )(elems: Frag*) = 
  {
    val identity = s"parameter_${Parameter.nextInputId}"
    div(`class` := "parameter",
      label(attr("for") := identity, name),
      tag(basetag)(attr("id") := identity, attr("name") := name, elems),
      onchange := { (e:dom.Event) => changeHandlers.foreach { _(e) } }
    )
  }
  def pulldown(
    name: String, 
    selected: Int
  )(options: (String, String)*) =
    input(name, "select")(
      options.zipWithIndex.map { case ((description, value), idx) => 
        option(
          attr("value") := value, 
          if(idx == selected) { attr("selected", raw = true) := "" } else { "" },
          description
        )
      }:_*
    )


}
object Parameter
{
  def apply(description: ParameterDescriptor, editor: ModuleEditor)
           (implicit owner: Ctx.Owner): Parameter =
  {
    description.datatype match {
      case "code" => new CodeParameter(description)
      case "colid" => new ColIdParameter(description, editor.module.visibleArtifacts.flatMap { x => x }, editor.parameters)
      case "dataset" => new DatasetParameter(description, editor.module
                                                                .visibleArtifacts
                                                                .map { _().mapValues { _.t } })
      case "list" => new ListParameter(description, this.apply(_, editor))
      case _      => new UnsupportedParameter(description)
    }
  }

  /**
   * Unflatten the flattened wire representation of the parameters
   * 
   * The Vizier 1.1 wire protocol flattens the parameters out into a single 
   * sequence, in particular affecting List and Record parameters.  This 
   * function unflattens the representation.
   */
  def collapse(descriptions: Seq[ParameterDescriptor]): Seq[ParameterDescriptor] =
  {
    val elements = mutable.Map[String, ParameterDescriptor]()
    descriptions.foreach { element => 
      elements += (element.id -> element)
      if(element.parent.isDefined) {
        if(elements contains element.parent.get){
          val parent = elements(element.parent.get)
          if(parent.elements.isDefined){
            parent.elements.get.push(element)
          } else {
            parent.elements = js.Array(element)
          }
        } else {
          println(s"WARNING: parameter ${element.id} has an invalid parent ${element.parent} (in ${elements.keys.mkString(", ")})")
        }
      }
    }
    elements
      .values
      .toSeq
      .filter { _.parent.isEmpty }
      .sortBy { _.index }
  }
  var nextInputIdValue: Long = -1l

  def nextInputId = { nextInputIdValue += 1; nextInputIdValue }
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
  def this(description: ParameterDescriptor)
  {
    this(
      description.id, 
      description.name, 
      description.language.getOrElse { "text" },
      description.required, 
      description.hidden
    )
  }

  val root = 
    div(
      textarea(
        // "code goes here...",
        OnMount { (n: dom.Node) => 
          CodeMirror.fromTextArea(n,
            js.Dictionary(
              "value" -> "this is a test",
              "mode" -> CodeParameter.CODEMIRROR_FORMAT.getOrElse(language, "text/plain"),
              "lineNumbers" -> true
            )
          ) 
        }
      )
    )
}
object CodeParameter
{
  val CODEMIRROR_FORMAT = Map(
    "python" -> "text/x-python",
    "scala" -> "text/x-scala",
    "sql" -> "text/x-sql",
    "markdown" -> "text/x-markdown"
  )
}

class ColIdParameter(
  val id: String, 
  val name: String, 
  schema: Rx[Seq[DatasetColumn]],
  val required: Boolean,
  val hidden: Boolean
) (implicit owner: Ctx.Owner) extends Parameter
{
  def this(description: ParameterDescriptor, datasets: Rx[Map[String, Artifact]], parameters: Seq[Parameter])
          (implicit owner: Ctx.Owner)
  {
    this(
      description.id,
      description.name,
      parameters.flatMap {
        case dsParameter:DatasetParameter => Some(dsParameter)
        case _ => None
      }.headOption
       .map { dsParameter =>
          Rx {
            dsParameter.selectedDataset() match {
              case None => Seq.empty
              case Some(dsName) => 
                datasets().get(dsName) match {
                  case None => 
                    println(s"WARNING: ColIdParameter $name used with an undefined artifact")
                    Seq.empty
                  case Some(dsArtifact) => 
                    dsArtifact.metadata match {
                      case Some(DatasetMetadata(columns)) =>
                        columns
                      case _ => 
                        println(s"WARNING: ColIdParameter $name used with a non-dataset artifact")
                        Seq.empty
                    }
                }
            }
          }
      }.getOrElse { 
        println(s"WARNING: ColIdParameter $name used with out an associated dataset")
        Var(Seq.empty) 
      },
      description.required,
      description.hidden
    )
  }

  val root = span(
    Rx {
      pulldown(name, 0)(
        (
          ("---" -> "") +:
          schema().map { col => 
            col.name -> col.id.toString
          }
        ):_*
      )
    }
  )
}

class DatasetParameter(
  val id: String, 
  val name: String, 
  datasets: Rx[Map[String, ArtifactType.T]],
  val required: Boolean,
  val hidden: Boolean,
)(implicit owner: Ctx.Owner) extends Parameter
{
  def this(description: ParameterDescriptor, datasets: Rx[Map[String, ArtifactType.T]])
          (implicit owner: Ctx.Owner)
  {
    this(
      description.id, 
      description.name, 
      datasets,
      description.required, 
      description.hidden
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

  val root = 
    Rx { 
      pulldown(
        name, 
        0, 
      )(
        (
          Seq("---" -> "") ++ 
          datasets().filter { _._2 == ArtifactType.DATASET }
                    .map { x => x._1 -> x._1 }
        ):_*
      )
    }

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
  titles: Seq[String],
  elements: Seq[() => Parameter], 
  val required: Boolean,
  val hidden: Boolean
)(implicit owner: Ctx.Owner)
  extends Parameter
{
  def this(description: ParameterDescriptor, getParameter: ParameterDescriptor => Parameter)
          (implicit owner: Ctx.Owner)
  {
    this(
      description.id,
      description.name, 
      description.elements.map { _.map { _.name }.toSeq }.getOrElse { Seq.empty },
      description.elements
                 .map { _.map { x => () => getParameter(x) }.toSeq }
                 .getOrElse { Seq.empty },
      description.required,
      description.hidden
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
  lastRow.trigger { println("LAST ROW CHANGED!")}

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
  def this(description: ParameterDescriptor)
  {
    this(
      description.id, 
      description.name, 
      description.datatype,
      description.required, 
      description.hidden
    )
  }
  val root = span(s"Unsupported parameter type: $dataType")
}
