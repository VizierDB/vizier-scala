package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.widgets.FontAwesome
import rx._
import info.vizierdb.serialized.{PythonEnvironmentDescriptor, PythonEnvironmentSummary}
import info.vizierdb.ui.network.API
import info.vizierdb.ui.Vizier
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.Expander
import info.vizierdb.serialized
import info.vizierdb.ui.widgets.ShowModal

class PythonSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  var environments = div(Spinner(size = 30)).render

  def load(): Unit = 
  {
    Vizier.api.configListPythonEnvs()
              .onComplete { 
                case Success(newEnvs) => 
                  environments.innerHTML = ""
                  for((env, summary) <- newEnvs){
                    environments.appendChild(Environment(env, summary).render)
                  }
                case Failure(err) => Vizier.error(err.getMessage())
              }
  }

  def Environment(name: String, summary: serialized.PythonEnvironmentSummary): dom.html.Element =
  {
    val details = Seq(
      "Python "+summary.pythonVersion
    ).mkString("; ")

    val descriptor = 
      Vizier.api.configGetPythonEnv(name)

    val list_id = s"packages_for_$name"

    div(`class` := "environment",

      // Title and Summary in the First row 
      div(`class` := "summary",
        Expander(list_id),               // The 'arrow' to expand the package list
        span(`class` := "label", name),
        span(`class` := "details", 
          " (",
          details,
          ")"
        ),
        span(`class` := "spacer"),

        // Action items in the first row
        a(`class` := "fabutton",
          FontAwesome("files-o"), href := "#", 
          onclick := { _:dom.Event => println("duplicate") }),
        a(`class` := "fabutton",
          FontAwesome("share-square-o"), href := "#", 
          onclick := { _:dom.Event => println("export") }),
      ),

      // Follow-up package list
      div(`class` := "packages closed",
        id := list_id,

        // Display the Future in a lazy-loading div.  It'll likely be loaded by 
        // the time the user clicks the arrow button, but let's be sure.
        Spinner.lazyLoad {
          descriptor.map { 
            case serialized.PythonEnvironmentDescriptor(
              pythonVersion,
              revision,
              packages
            ) =>
            table(
              thead(
                tr(
                  th("Package"),
                  th("Version"),
                  th("Actions")
                )
              ),
              tbody(

                // One row per package
                packages.map { case serialized.PythonPackage(name, version) =>
                  tr(
                    td(
                      a(
                        href := s"https://pypi.org/project/$name/",
                        target := "_blank",
                        name
                      )
                    ),
                    td(version),
                    td(`class` := "actions",
                      a(`class` := "fabutton",
                        FontAwesome("trash"), href := "#", 
                        onclick := { _:dom.Event => println(s"remove $name") }),
                      a(`class` := "fabutton",
                        FontAwesome("level-up"), href := "#", 
                        onclick := { _:dom.Event => println(s"upgrade $name") }),
                    )
                  )
                } :+
                // And follow it with an "add package button" row
                tr(
                  td(
                    `class` := "addpackage",
                    colspan := 3,
                    button(
                      FontAwesome("plus-circle"),
                      onclick := { _:dom.Event => println("install") },
                    )
                  )
                )
              )
            ).render
          }
        }
      )
    ).render
  }

  def newEnvironment(): Unit =
  {
    val newEnvName = 
      input(`type` := "text",
            pattern := "[a-zA-Z][a-zA-Z0-9]",
            attr("title") := "Alphanumeric, starting with a letter"
          ).render
    ShowModal.confirm(
      div("Name?"),
      newEnvName
    ){ 
      println("create new environment: "+newEnvName.value)
    }
  }

  def title = "Python"

  val root = div(`class` := "python",
    div(`class` := "group",
      div(`class` := "title", "Environments"),
      environments,
      div(`class` := "new_environment"),
      button(
        "New Environment",
        onclick := { _:dom.Event => newEnvironment() }
      )
    )
  ).render

}
