package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.widgets.FontAwesome
import rx._
import info.vizierdb.serialized.PythonEnvironment
import info.vizierdb.ui.network.API
import info.vizierdb.ui.Vizier
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.Expander

class PythonSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
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

  def Environment(): dom.html.Element =
  {
    val name = "System"
    val details = Seq(
      "Python 3.7.2",
    ).mkString("; ")

    val packages = Seq[(String,String)](
      ("astor","0.8.1"),
      ("bcrypt","3.2.2"),
      ("bokeh","2.4.2"),
      ("cffi","1.15.0"),
      ("cryptography","37.0.2"),
      ("cycler","0.11.0"),
      ("evdev","1.5.0"),
      ("fonttools","4.33.3"),
      ("Jinja2","3.1.2"),
      ("kiwisolver","1.4.2"),
      ("libevdev","0.10"),
      ("MarkupSafe","2.1.1"),
      ("matplotlib","3.5.2"),
      ("numpy","1.22.3"),
      ("packaging","21.3"),
      ("pandas","1.4.2"),
      ("paramiko","2.11.0"),
      ("Pillow","9.1.0"),
      ("pip","21.1.1"),
      ("py4j","0.10.9.3"),
      ("pyarrow","3.0.0"),
      ("pycparser","2.21"),
      ("PyNaCl","1.5.0"),
      ("pynput","1.7.6"),
      ("pyparsing","3.0.9"),
      ("pyspark","3.2.1"),
      ("python-dateutil","2.8.2"),
      ("python-xlib","0.31"),
      ("pytz","2022.1"),
      ("PyYAML","6.0"),
      ("remarkable-mouse","7.0.2"),
      ("screeninfo","0.8"),
      ("setuptools","56.0.0"),
      ("Shapely","1.8.2"),
      ("six","1.16.0"),
      ("tornado","6.1"),
      ("typing-extensions","4.2.0"),
      ("wheel","0.37.1"),
    )

    val list_id = s"packages_for_$name"

    div(`class` := "environment",
      div(`class` := "summary",
        Expander(list_id),
        span(`class` := "label", name),
        span(`class` := "details", 
          " (",
          details,
          ")"
        ),
        span(`class` := "spacer"),
        a(FontAwesome("files-o"), href := "#", 
          onclick := { _:dom.Event => println("duplicate") }),
        a(FontAwesome("share-square-o"), href := "#", 
          onclick := { _:dom.Event => println("export") }),
      ),
      table(`class` := "packages closed",
        id := list_id,
        thead(
          tr(
            th("Package"),
            th("Version"),
            th("Actions")
          )
        ),
        tbody(
          packages.map { case (name, version) =>
            tr(
              td(name),
              td(version),
              td(`class` := "actions",
                a(FontAwesome("trash"), href := "#", 
                  onclick := { _:dom.Event => println(s"remove $name") }),
                a(FontAwesome("plus-square-o"), href := "#", 
                  onclick := { _:dom.Event => println(s"upgrade $name") }),
              )
            )
          }
        )
      )
    ).render
  }


  def title = "Python"

  val root = div(`class` := "python",
    div(`class` := "group",
      div(`class` := "title", "Environments"),
      Environment()
    )
  ).render

}
