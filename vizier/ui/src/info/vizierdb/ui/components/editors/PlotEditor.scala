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

override val editorFields: Frag = {
  div(
    `class` := "table_of_contents",
    span(`class` := "reactive",
      div(`class` := "contents", id := "table_of_contents",
        div(`class` := "module_list",
          h3(`class` := "title", "Workflow"),
          ol(`class` := "the_modules",
            li(`class` := "tentative",
              span(span(`class` := "reactive", "plot.line-chart"))
            )
          )
        ),
        div(`class` := "artifact_list",
          h3(`class` := "title", "Artifacts"),
          span(`class` := "reactive", div(`class` := "the_artifacts"))
        )
      )
    ),
    div(`class` := "workflow",
      span(`class` := "reactive",
        div(`class` := "workflow_content",
          span(`class` := "reactive", 
            div(`class` := "first",
              div(`class` := "inter_module",
                div(`class` := "elements",
                  span(`class` := "separator", "———"),
                  button(),
                  span(`class` := "separator", "———")
                )
              ),
              span(`class` := "reactive", div())
            )
          ),
          div(`class` := "module_list",
            div(
              div(`class` := "module tentative", id := "element_0",
                div(`class` := "menu",
                  div(`class` := "spacer")
                ),
                div(`class` := "module_body",
                  span(`class` := "reactive",
                    div(`class` := "module_editor",
                      div(width := "100%", 
                        fieldset(
                          legend("Lines"),
                          table(`class` := "parameter_list",
                            thead(
                              tr(
                                th("Dataset:"),
                                th(span(span(`class` := "reactive"))),
                                th(button(i(`class` := "fa fa-ellipsis-h", aria.hidden := "true"))),
                                th()
                              )
                            ),
                            tbody(
                              tr(
                                td(span(span(`class` := "reactive"))),
                                td(span(span(`class` := "reactive"))),
                                td(span(span(`class` := "reactive"))),
                                td(div(`class` := "parameter")),
                                td(div(`class` := "parameter")),
                                td(div(`class` := "parameter"))
                              )
                            )
                          )
                        )
                      ),
                      div(`class` := "editor_actions",
                        button(`class` := "cancel",
                          i(`class` := "fa fa-arrow-left", aria.hidden := "true"), " Back"
                        ),
                        div(`class` := "spacer"),
                        button(`class` := "save",
                          i(`class` := "fa fa-cogs", aria.hidden := "true"), " Plot"
                        )
                      )
                    )
                  )
                )
              ),
              div(`class` := "inter_module",
                div(`class` := "elements",
                  span(`class` := "separator", "———"),
                  button(i(`class` := "fa fa-pencil-square-o", aria.hidden := "true")),
                  button(i(`class` := "fa fa-plus", aria.hidden := "true")),
                  button(i(`class` := "fa fa-binoculars", aria.hidden := "true")),
                  span(`class` := "separator", "———")
                )
              )
            )
          )
        )
      )
    )
  )
}


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

