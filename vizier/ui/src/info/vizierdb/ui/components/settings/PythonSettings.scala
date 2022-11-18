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
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView


class PythonSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  sealed trait PythonPackageEntry { def root: dom.html.Element }

  case class PythonPackage(pkg: serialized.PythonPackage) extends PythonPackageEntry
  {
    val version = Var[Option[dom.html.Input]](None)

    val root =
      tr(
        td(
          a(
            href := s"https://pypi.org/project/${pkg.name}/",
            target := "_blank",
            pkg.name
          )
        ),
        td(
          version.map {
            _.getOrElse { span(pkg.version).render }
          }.reactive
        ),
        td(`class` := "actions",
          a(`class` := "fabutton",
            FontAwesome("trash"), href := "#", 
            onclick := { _:dom.Event => println(s"remove $name") }),
          a(`class` := "fabutton",
            FontAwesome("level-up"), href := "#", 
            onclick := { _:dom.Event => println(s"upgrade $name") }),
        )
      ).render
  }

  case class NewPackage() extends PythonPackageEntry
  {
    val name = input(`type` := "text").render
    val version = input(`type` := "text").render

    val root = 
      tr(`class` := "tentative_package",
        td(name),
        td(version),
        td(`class` := "actions",
          a(`class` := "fabutton",
            FontAwesome("trash"), href := "#", 
            onclick := { _:dom.Event => println(s"remove $name") }),
        )        
      ).render
  }

  case class PythonEnvironmentEntry(name: String, version: String)
  {
    val packages = 
      RxBuffer[PythonPackageEntry]()

    val list_id = s"packages_for_$name"

    val packagesView = 
      RxBufferView(tbody().render, packages.rxMap { _.root })

    val root = 
      div(`class` := "environment",

        // Title and Summary in the First row 
        div(`class` := "summary",
          Expander(list_id),               // The 'arrow' to expand the package list
          span(`class` := "label", name),
          span(`class` := "details", 
            " (",
            version,
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
          table(
            thead(
              tr(
                th("Package"),
                th("Version"),
                th("Actions")
              )
            ),
            packagesView.root
          ),
          div(
            `class` := "addpackage",
            button(
              FontAwesome("plus-circle"),
              onclick := { _:dom.Event => println("install") },
            )
          )
        )
      ).render
  }

  val environments = 
    RxBuffer[PythonEnvironmentEntry]()

  val environmentsView = 
    RxBufferView(div().render, environments.rxMap { _.root })

  def load(): Unit = 
  {
    Vizier.api.configListPythonEnvs()
              .onComplete { 
                case Success(newEnvs) => 
                  environments.clear()
                  environments.insertAll(0, 
                    newEnvs.map { case (env, summary) =>
                      PythonEnvironmentEntry(env, summary.pythonVersion)
                    }
                  )
                case Failure(err) => 
                  Vizier.error(err.getMessage())
              }
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
      environmentsView.root,
      div(`class` := "new_environment"),
      button(
        "New Environment",
        onclick := { _:dom.Event => newEnvironment() }
      )
    )
  ).render

}
