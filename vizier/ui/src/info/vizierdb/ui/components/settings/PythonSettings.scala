package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.widgets.FontAwesome

class PythonSettings(parent: SettingsView) extends SettingsTab
{

  def Environment(): dom.html.Element =
  {
    val details = Seq(
      "Python 3.7.2",
      "Used by __ cells in __ projects",
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

    div(`class` := "environment",
      div(`class` := "summary",
        span(`class` := "label", "System"),
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
      table(`class` := "packages",
        thead(
          tr(
            th("Package"),
            th("Version"),
            th()
          )
        ),
        tbody(
          packages.map { case (name, version) =>
            tr(
              td(name),
              td(version),
              td(
                a(FontAwesome("trash"), href := "#", 
                  onclick := { _:dom.Event => println("remove") }),
                a(FontAwesome("plus-square-o"), href := "#", 
                  onclick := { _:dom.Event => println("upgrade") }),
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
  def load(): Unit = {}
}