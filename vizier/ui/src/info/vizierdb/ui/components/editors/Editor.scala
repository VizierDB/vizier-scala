package info.vizierdb.ui.components.editors

import info.vizierdb.ui.components._
import info.vizierdb.serialized.{ CommandArgument, CommandArgumentList }
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
import info.vizierdb.serializers._
import info.vizierdb.types._
import info.vizierdb.ui.components.EnumerableParameter

class Editor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "data",
  val commandId: String = "test"
)(implicit owner: Ctx.Owner)
  extends ModuleEditor
  with Logging
{

  override val editorFields: Frag = 
    div(
      width := "100%",
      h4("Custom Editor Fields"),
      div("This is a dummy HTML representation for the custom editor."),
      div(
        label("Sample Input: "),
        input(`type` := "text", id := "sample-input")
      ),
    )

  override def currentState: Seq[CommandArgument] = {
    val input = dom.document.getElementById("sample-input").asInstanceOf[dom.html.Input]
    Seq(
        dataset.toArgument,
        CommandArgument("sampleKey", JsString(input.value)),
    )
  }

  val dataset = 
    new ArtifactParameter(
      id = "dataset",
      name = "Dataset: ",
      artifactType = ArtifactType.DATASET,
      artifacts = delegate.visibleArtifacts
                          .map { _.mapValues { _._1.t } },
      required = true,
      hidden = false,
    )

  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    for ( arg <- arguments )
    { 
      arg.id match {
        case "dataset" => 
          dataset.set(arg.value)
        case "sampleKey" => 
          dom.document.getElementById("sample-input").asInstanceOf[dom.html.Input].value = arg.value.as[String]
      }
    }
  }
}

