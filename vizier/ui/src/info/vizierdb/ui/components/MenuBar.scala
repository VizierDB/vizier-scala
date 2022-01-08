package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._


class MenuBar(project: Project)(implicit owner: Ctx.Owner)
{  
  def separator(): Frag = li(`class` := "separator")

  val menus = Rx {
    val active:Option[Identifier] = project.activeBranch();
    Seq(
      Menu(s"Project [${project.projectName()}]").items(
        Item("Project List", () => dom.window.location.href = "index.html"),
        Item("Rename...", () => println("Clicked Rename")),
        Item("Export...", () => println("Clicked Export")),
      ),
      Menu(s"Branch [${project.activeBranchName()}]").items(
        Item("History", () => println("Clicked History")),
        Separator()
      ).items(
        project.branches()
               .values
               .map { description => 
                  Item(
                    description.name, 
                    () => println(s"Switch to ${description.name}"),
                    highlight = Some(description.id) == active
                  )
               }.toSeq:_*
      )
    )
  }

  def deactivateAllMenus(): Unit =
  {
    for(menu <- menus.now){
      if(menu.active.now) { menu.active() = false }
    }
  }


  case class Menu(title: String, elements: Seq[MenuElement] = Seq.empty)
  {
    val active = Var[Boolean](false)

    lazy val root:Frag = 
      li(
        `class` := "menu",
        title,
        onclick := { _:dom.Event => 
          val oldState = active.now
          deactivateAllMenus()
          if(!oldState) { active() = true }
        },
        Rx { 
          if(active()){ ul(elements.map { _.root }.toSeq:_*) }
          else { ul(`class` := "placeholder", visibility := "hidden") }
        }
      )

    def items(newElements: MenuElement*) = copy(title, elements ++ newElements)
  }

  trait MenuElement { val root: Frag }

  case class Item(
    title: String, 
    effect: () => Unit, 
    highlight: Boolean = false
  ) extends MenuElement 
  {
    val root: Frag = 
      li(
        `class` := s"menu_item${if(highlight){ " highlight" } else { "" }}",
        title, 
        onclick := { _:dom.Event => effect() })
  }

  case class Separator() extends MenuElement
  {
    val root: Frag = li(`class` := "menu_separator")
  }

  val root:Frag = 
    tag("nav")(
      Rx { 
        ul( (
          Seq(id := "main_menu", `class` := "menu_bar") ++
          menus().map { _.root } 
        ):_*)
      }
    )

}