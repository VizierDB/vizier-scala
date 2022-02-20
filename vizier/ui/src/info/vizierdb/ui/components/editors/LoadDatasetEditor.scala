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

class LoadDatasetEditor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "data",
  val commandId: String = "load"
)(implicit owner: Ctx.Owner) 
  extends ModuleEditor
  with Logging
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  override def loadState(arguments: Seq[CommandArgument]): Unit = ???

  override def currentState: Seq[CommandArgument] = ???

  val urlField = input(`type` := "text", 
                       name := "url",
                       onchange := { _:dom.Event => urlChanged }).render
  val directoryStack = Var[List[FileBrowser]](Nil)
  val format = Var[String]("csv")

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
          label(`for` := "url", "URL: "),
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
        Rx { 
          val active = format() 
          println(s"Active format is $active")
          select(
            name := "file_format",
            DatasetFormat.ALL.map { case (label, id) =>
              if(id == active){
                option(value := id, selected, label)
              } else {
                option(value := id, label)
              }
            }
          )
        }.reactive,
      ),
    )

  def setURL(path: String)
  {
    urlField.value = path
    urlChanged
  }

  def urlChanged: Unit =
  {
    println(s"Url field now ${urlField.value}")
    urlField.value.split("\\.").last match {
      case "json" => format() = DatasetFormat.JSON
      case "csv" => format() = DatasetFormat.CSV
      case _ => format() = DatasetFormat.Text
    }
    println(s"URL Value now : ${urlField.value}")
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
}