package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import info.vizierdb.serialized.PythonEnvironment
import info.vizierdb.ui.network.API
import info.vizierdb.ui.Vizier
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import info.vizierdb.ui.widgets.Spinner

class PythonSettings(parent: SettingsView) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  var environments = div(Spinner(size = 30)).render

  def Environment(name: String): dom.html.Element =
  {
    println(s"Environment: $name")
    val envElement = div(
      `class` := "setting", 
    ).render

    return envElement
  }

  def load(): Unit = 
  {
    Vizier.api.configListPythonEnvs()
              .onComplete { 
                case Success(newEnvs) => 
                  environments.innerHTML = ""
                  for(env <- newEnvs){
                    environments.appendChild(Environment(env).render)
                  }
                case Failure(err) => Vizier.error(err.getMessage())
              }
  }

  def title = "Python"

  val root = div(`class` := "python",
    div(`class` := "group",
      div(`class` := "title", "Environments"),
      environments,
      div(`class` := "setting", 
        button("New")
      )
    )
  ).render

}