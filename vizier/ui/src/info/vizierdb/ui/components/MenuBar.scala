package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome


class MenuBar(project: Project)(implicit owner: Ctx.Owner)
{  



  def Menu(clazz: String, title: Modifier*)(items: Frag*) = 
  {
    var contents:Seq[Modifier] = title
    contents = (`class` := clazz) +: contents
    contents = contents :+ ul(items:_*)
    div(contents:_*)
  }

  def MenuItem(title: String, action: () => Unit, icon: String = null): Frag =
  {
    var contents = Seq[Modifier](
      title, onclick := { _:dom.Event => action() }
    )
    if(icon != null){ contents = FontAwesome(icon) +: contents }
    li(contents:_*)
  }

  def Separator: Frag = li(`class` := "separator")

  val root = 
    tag("nav")(id := "menu_bar",
      a(`class` := "left item", href := "index.html", img(src := "vizier.svg")),
      Menu("left item", div(`class` := "text", project.projectName.reactive))(
        MenuItem("Rename...", { () => println("Rename!")}),
        MenuItem("Duplicate...", { () => println("Duplicate!")}),
      ),
      Menu("left item", FontAwesome("play-circle"))(
        MenuItem("Stop Running", { () => println("Stop")}, icon = "stop"),
        MenuItem("Freeze Everything", { () => println("Freeze")}, icon = "snowflake-o"),
        MenuItem("Thaw Everything", { () => println("Thaw")}, icon = "sun-o"),
      ),
      Rx {
        Menu("left item", FontAwesome("code-fork"))(
          (
            Seq(
              MenuItem("Rename Branch...", { () => println("Rename branch")}),
              MenuItem("Create Branch...", { () => println("Stop")}),
              MenuItem("Project History", { () => println("Stop")}, icon = "history"),
              Separator,
            ) ++ project.branches().map { case (id, branch) => 
              MenuItem(branch.name, { () => println(s"Switch to branch $id") }, icon = "code-fork")
            }
          ):_*
        )
      }.reactive,
      Menu("left item", FontAwesome("share-alt"))(
        MenuItem("Export Project...", { () => println("Export...") }),
        MenuItem("Present Project", { () => println("Present...") }),
      ),
      Menu("left item", FontAwesome("wrench"))(
        MenuItem("Python Settings", { () => println("Python Settings") }),
        MenuItem("Scala Settings", { () => println("Scala Settings") }),
        Separator,
        MenuItem("Spark Status", { () => println("Spark Status") }),
      ),
      div(`class` := "spacer"),
      a(`class` := "right item", href := "https://www.github.com/VizierDB/vizier-scala/wiki", FontAwesome("question-circle")),
    ).render

}