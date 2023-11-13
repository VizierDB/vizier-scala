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

class PlotEditor(
    val delegate: ModuleEditorDelegate,
    val packageId: String = "data",
    val commandId: String = "plot"
)(implicit owner: Ctx.Owner)
    extends ModuleEditor
    with Logging
{
    override val editorFields: Frag = {
        div(
            label("Dataset: "),
            dataset.editor,
            label("X-axis: "),
            xInput.editor,
            label("Y-axis: "),
            yInput.editor
        )
    }

    override def currentState: Seq[CommandArgument] = {
        Seq(
            dataset.toArgument,
            xInput.toArgument,
            yInput.toArgument
        )
    }

    override def loadState(arguments: Seq[CommandArgument]): Unit = {
        for (arg <- arguments) {
            arg.id match {
                case "dataset" => dataset.set(arg.value)
                case "xInput" => xInput.set(arg.value)
                case "yInput" => yInput.set(arg.value)
            }
        }
    }

    val dataset = new ArtifactParameter(
        id = "dataset",
        name = "Dataset: ",
        artifactType = ArtifactType.DATASET,
        artifacts = delegate.visibleArtifacts.map(_.mapValues(_._1.t)),
        required = true,
        hidden = false
    )

    val xInput = new StringParameter(
        id = "xInput",
        name = "X-axis: ",
        value = "",
        required = true,
        hidden = false
    )

    val yInput = new StringParameter(
        id = "yInput",
        name = "Y-axis: ",
        value = "",
        required = true,
        hidden = false
    )
}
