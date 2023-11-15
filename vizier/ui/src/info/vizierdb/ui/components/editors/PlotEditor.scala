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
        div(`class` := "module_list",
        div(
            div(`class` := "module tentative", id := "element_0",
            div(`class` := "menu",
                div(`class` := "spacer")
            ),
            div(`class` := "module_body",
                span(`class` := "reactive",
                div(`class` := "module_editor",
                    div(style := "width: 100%;",
                    div(style := "width: 100%;",
                        fieldset(
                        legend("Lines"),
                        table(`class` := "parameter_list",
                            thead(
                            tr(
                                th("Dataset: "),
                                th("X:"),
                                th("Y:"),

                                th(
                                button(
                                    i(`class` := "fa fa-ellipsis-h", aria.hidden := "true")
                                )
                                ),
                                th()
                            )
                            ),
                            tbody(
                            // ... tbody content
                            )
                        )
                        )
                    )
                    ),
                    div(`class` := "editor_actions",
                    button(`class` := "cancel",
                        i(`class` := "fa fa-arrow-left", aria.hidden := "true"), " Back"
                        // onclick event handler
                    ),
                    div(`class` := "spacer"),
                    button(`class` := "save",
                        i(`class` := "fa fa-cogs", aria.hidden := "true"), " Plot"
                        // onclick event handler
                    )
                    )
                )
                )
            )
            ),
            div(`class` := "inter_module",
            div(`class` := "elements",
                span(`class` := "separator", "———"),
                button(
                i(`class` := "fa fa-pencil-square-o", aria.hidden := "true")
                // onclick event handler
                ),
                button(
                i(`class` := "fa fa-plus", aria.hidden := "true")
                // onclick event handler
                ),
                button(
                i(`class` := "fa fa-binoculars", aria.hidden := "true")
                // onclick event handler
                ),
                span(`class` := "separator", "———")
            )
            )
        )
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

    override def loadState(arguments: Seq[CommandArgument]): Unit = {
        for (arg <- arguments) {
            arg.id match {
                case "dataset" => dataset.set(arg.value)
            }
        }
    }

    override def currentState: Seq[CommandArgument] = {
        Seq(
            dataset.toArgument,
            )
        }
    }