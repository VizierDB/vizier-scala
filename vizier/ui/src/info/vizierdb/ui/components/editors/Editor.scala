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

class Editor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "plot",
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
        input(`type` := "text")
      ),
    )

  override def currentState: Seq[CommandArgument] = {
    Seq(
        CommandArgument(
            "sampleKey",
            Json.toJson("sampleValue")
        )
    )
}
  

  override def loadState(arguments: Seq[CommandArgument]): Unit = {
    for(arg <- arguments) {
      println(s"Loading argument with id: ${arg.id} and value: ${arg.value}")
    }
  }
}





