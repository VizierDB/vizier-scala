package info.vizierdb.ui.components.editors

import info.vizierdb.ui.components._
import info.vizierdb.serialized.CommandArgument
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.serialized.CommandDescription
import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.types.MIME
import info.vizierdb.ui.Vizier
import scala.util.Success
import scala.util.Failure
import info.vizierdb.util.Logging
import info.vizierdb.types.DatasetFormat
import play.api.libs.json._

class LoadDatasetEditor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "data",
  val commandId: String = "load"
)(implicit owner: Ctx.Owner) 
  extends ModuleEditor
  with Logging
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val urlField = input(`type` := "text", 
                       name := "url",
                       placeholder := "https://url/of/file.csv ...OR... path/to/file.csv",
                       onchange := { _:dom.Event => urlChanged }).render
  val directoryStack = Var[List[FileBrowser]](Nil)
  val format = 
    select(
      name := "file_format",
      DatasetFormat.ALL.map { case (label, id) =>
        option(value := id, label)
      },
      onchange := { _:dom.Event => formatChanged }
    ).render:dom.html.Select

  val sparkOptions: Parameter = 
    new ListParameter("loadOptions",
      "Spark Load Options",
      Seq[String]("Key", "Value"),
      Seq[() => Parameter](
        { () => new StringParameter(
                  "loadOptionKey",
                  "Key",
                  true,
                  false
                ) },
        { () => new StringParameter(
                  "loadOptionValue",
                  "Value",
                  true,
                  false
                ) },
      ),
      false,
      false
    )
  val datasetName =
    new StringParameter(
      "name",
      "Dataset Name: ",
      false,
      false
    )
  val schema = new ListParameter("schema",
    "Schema (leave empty to guess)",
    Seq[String]("Column", "Data Type"),
    Seq[() => Parameter](
      { () => new StringParameter(
                "schema_column",
                "Column",
                true,
                false
              ) },
      { () => new DataTypeParameter(
                "schema_datatype",
                "Data Type",
                true,
                false
              ) },
    ),
    false,
    false
  )

  val optionalParameters = Seq[(Set[String], Parameter)](
    Set(DatasetFormat.CSV) ->
      new BooleanParameter("loadDetectHeaders", "File has Headers: ", true, false, true),
    Set(DatasetFormat.CSV) ->
      new StringParameter("loadDelimiter", "Field Delimiter: ", true, false, ","),
    Set(DatasetFormat.CSV) ->
      new BooleanParameter("loadInferTypes", "Guess Schema: ", true, false, true),
  )
  val optionalParameterByKey = 
    optionalParameters.map { param => param._2.id -> param._2 }.toMap

  val activeParameters = Var(Seq[Parameter](sparkOptions))
  formatChanged

  override val editorFields: Frag = 
    div(`class` := "module editable load_dataset",
      div(`class` := "header",
        // wrapper needed for flexbox
        div(`class` := "back_button_wrapper",
          Rx { 
            button(
              if(directoryStack().size <= 1) { disabled } 
              else { attr("ignored") := "ignored" },
              FontAwesome("arrow-left"),
              onclick := { _:dom.Event =>
                if(directoryStack.now.size > 1){
                  directoryStack() = directoryStack.now.tail
                  setURL(directoryStack.now.headOption
                                       .map { _.externalPath }
                                       .getOrElse { "" })
                }
              }
            )
          }.reactive
        ),
        div(`class` := "url", 
          label(`for` := "url", "File or URL: "),
          urlField
        ),
      ),
      div(`class` := "file_browser",
        Rx { 
          directoryStack().headOption match {
            case None => Spinner(30)
            case Some(browser) => browser.root
          }
        }.reactive
      ),
      div(`class` := "format_field", 
        label("Format: ", `for` := "file_format"),
        format
      ),
      Rx {
        div(
          activeParameters().map { param =>
            div(`class` := "format_field", param.root) 
          }
        )
      }.reactive,
      div(`class` := "schema", schema.root)
    )


  def setURL(path: String)
  {
    urlField.value = path
    urlChanged
  }

  def urlChanged: Unit =
  {
    val file = urlField.value.split("/").last
    val components = file.split("\\.")
    components.last match {
      case "json" => format.value = DatasetFormat.JSON
      case "csv"  => format.value = DatasetFormat.CSV
      case _      => format.value = DatasetFormat.Text
    }
    datasetName.setHint(components.head)
    formatChanged
  }

  def formatChanged: Unit =
  {
    println(s"Format now ${format.value}")
    val fmt = format.value
    activeParameters() =
      Seq( datasetName ) ++
      optionalParameters.filter { _._1(fmt) }
                        .map { _._2 } ++
      Seq( sparkOptions )
    println(s"Format done")
  }

  def visitDirectory(path: String = "", externalPath: String = "") =
  {
    Vizier.api
          .fsGet(path)
          .onComplete {
            case Success(files) => 
              directoryStack.update { new FileBrowser(files, externalPath) :: _ }
            case Failure(err) => 
              logger.error(s"${err.getClass.getSimpleName}: ${err.getMessage}")
          }
  }
  visitDirectory("")

  val GENERIC_IMAGE = "image/.*".r

  def ellipsize(str: String) =
    if(str.length() > 16){
      str.take(6)+"..."+str.takeRight(6)
    } else {
      str
    }

  case class FileBrowser(files: Seq[FilesystemObject], externalPath: String)
  {
    val root = 
      div(`class` := "file_list", 
        files.map { file => 
          div(`class` := "file",
            FontAwesome(
              file.icon.getOrElse {
                file.mimeType match {
                  case MIME.DIRECTORY => "folder-o"
                  case MIME.JAVASCRIPT => "file-code-o"
                  case GENERIC_IMAGE() => "file-image-o"
                  case _ => "file-o"
                }
              }
            ),
            span(`class` := "label", ellipsize(file.label)),
            if(file.hasChildren){
              onclick := { _:dom.Event => 
                setURL(file.externalPath)
                visitDirectory(file.internalPath, file.externalPath) 
              }
            } else {
              onclick := { _:dom.Event => 
                setURL(file.externalPath)
              }
            }
          )
        }
      ).render
  }

  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    print(s"loadState: ${arguments.mkString("\n")}")
    for(arg <- arguments){
      arg.id match {
        case "file" => 
          (arg.value \ "url").asOpt[String] match {
            case Some(f) => urlField.value = f
            case None    => println("ERROR: Internal files are not supported yet")
          }
        case "name"       => datasetName.set(arg.value)
        case "loadFormat" => format.value = arg.value.as[String]
        case "loadOptions" => sparkOptions.set(arg.value)
        case "loadDataSourceErrors" => ()
        case "schema" => println(s"Schema: ${arg.value}"); schema.set(arg.value)
        case x if optionalParameterByKey contains x => 
          optionalParameterByKey(x).set(arg.value)
        case x => println(s"ERROR: Unsupported load editor parameter $x")
      }
    }
  }

  override def currentState: Seq[CommandArgument] = 
  {
    Seq(
      CommandArgument(
        "file", Json.obj("url" -> JsString(urlField.value))
      ), 
      datasetName.toArgument,
      CommandArgument(
        "loadFormat" -> JsString(format.value)
      ),
      CommandArgument(
        "loadInferTypes" -> JsBoolean(true)
      ),
      schema.toArgument,
      sparkOptions.toArgument
    ) ++ optionalParameters.map { _._2.toArgument }
  }
}
