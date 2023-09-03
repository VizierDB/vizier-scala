package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.serialized
import rx._
import info.vizierdb.util.Trie
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.Tooltip

class CommandList(
  packages: Seq[serialized.PackageDescription], 
  module: TentativeModule
)(implicit owner:Ctx.Owner){

  val keywords = 
    Trie.ofSeq[String](
      packages.flatMap { pkg =>
        val packageKeywords = pkg.name.toLowerCase.split("[^a-zA-Z]") :+ pkg.id
        pkg.commands.toSeq
           .filterNot { _.hidden.getOrElse { false } }
           .flatMap { cmd => 
              val commandKeywords =
                packageKeywords ++
                  cmd.name.toLowerCase.split(" +") :+ 
                  cmd.id
              val commandKey = s"${pkg.id}.${cmd.id}"
              commandKeywords.map { _ -> commandKey }
           }
      }
    )

  val commands:Map[(String, String), serialized.PackageCommand] = 
    packages.flatMap { pkg => 
      pkg.commands.map { cmd =>
        (pkg.id, cmd.id) -> cmd
      }
    }
    .toMap

  val searchField =
    input(
      placeholder := "Search commands...",
      onkeydown := { _:dom.Event =>  
                        dom.window.requestAnimationFrame { _ => refreshSelectedCommands() } }
    ).render

  val selectedCommands = Var[Set[String]](Set.empty)

  def refreshSelectedCommands():Unit =
  {
    val term = searchField.value:String
    if(term.isEmpty()){ selectedCommands() = Set[String]() }
    else {
      selectedCommands() = keywords.prefixMatch(term)
    }
  }

  val root = 
    div(`class` := "select_command", 
      div(`class` := "command_search",
        FontAwesome("search"),
        searchField,
        Rx { 
          if(selectedCommands().isEmpty) {
            button(
              FontAwesome("ban"),
              visibility := "hidden"
            )
          } else {
            button(
              FontAwesome("ban"),
              onclick := { _:dom.Event =>
                searchField.value = ""
                dom.window.requestAnimationFrame( _ => refreshSelectedCommands() )
              }
            )
          }
        }.reactive
      ),
      Rx {
        val activeSelection = selectedCommands()
        table(
          `class` := "command_list",
          CommandList.DEFAULT.map { case (group, specialCommands) =>
            tr(
              th(`class` := "group", group),
              td(`class` := "commands", specialCommands.map { cmd => cmd.commandButton({
                () => 
                  module.selectCommand(cmd.packageId, 
                    commands(cmd.packageId -> cmd.commandId)
                  )
              })})
            )
          },
          tr(
            th(`class` := "group", "Specialized"),
            td(`class` := "commands",
              ul(
                packages.map { pkg => 
                  val commands = 
                    pkg.commands.toSeq
                        .filterNot { _.hidden.getOrElse { false } }
                        .filterNot { cmd => CommandList.IS_DEFAULT(pkg.id -> cmd.id)}
                        .map { cmd => 
                          val isSuggested = 
                            if(activeSelection.isEmpty){
                              cmd.suggest.getOrElse(false)
                            } else {
                              activeSelection(s"${pkg.id}.${cmd.id}")
                            }
                          button(
                            cmd.name, 
                            `class` := s"command${if(isSuggested){ " suggested" } else { "" }}",
                            onclick := { 
                              (e: dom.MouseEvent) => module.selectCommand(pkg.id, cmd)
                            })
                        }
                  if(commands.isEmpty) { None }
                  else { Some( li(b(pkg.name), div(commands)) )}
                },
              )
            )
          )
        )
      }.reactive,
      div(`class` := "editor_actions",
        button(
          FontAwesome("ban"),
          " Cancel", 
          `class` := "cancel",
          onclick := { (e: dom.MouseEvent) => module.cancelSelectCommand() }
        ).render
      )
    )

  def simulateClick(packageId: String, commandId: String) =
  {
    packages.find { _.id == packageId } match {
      case Some(pkg) => 
        pkg.commands.find { _.id == commandId } match {
          case Some(cmd) => module.selectCommand(packageId, cmd)
          case None => println(s"SIMULATE CLICK ON TENTATIVE MODULE FAILED: NO COMMAND $commandId")
        }
      case None => println(s"SIMULATE CLICK ON TENTATIVE MODULE FAILED: NO PACKAGE $packageId")
    }
  }
}

object CommandList
{
  case class SpecialCommand(
    label: String,
    icon: String,
    packageId: String,
    commandId: String,
    description: String
  )
  {
    lazy val iconUrl = Vizier.links.asset(s"icons/${icon}.svg")

    def tuple = (packageId, commandId)
    def commandButton(handler: () => Unit): dom.html.Button = 
      button(
        `class` := "with_icon",
        onclick := { (_:dom.Event) => handler() },
        img(`class` := "icon", src := iconUrl),
        label,
        Tooltip(description)
      ).render
  }

  val DEFAULT: Seq[(String, Seq[SpecialCommand])] = Seq(
    "Import" -> Seq(
      SpecialCommand(label = "Dataset", icon = "load_table", packageId = "data", commandId = "load", description = "Import a tabular data file (e.g., CSV) or previously exported dataframe"),
      // SpecialCommand(label = "Import File", icon = "load", packageId = "data", commandId = "load"),
    ),
    "Script" -> Seq(
      SpecialCommand(label = "SQL",    icon = "sql",       packageId = "sql",    commandId = "query", description = "Generate or update a dataset using SQL"),
      SpecialCommand(label = "Python", icon = "python",    packageId = "script", commandId = "python", description = "Transform or generate data using Python"),
      SpecialCommand(label = "Scala",  icon = "scala",     packageId = "script", commandId = "scala", description = "Transform or generate data using Scala"),
    ),
    "Visualize" -> Seq(
      SpecialCommand(label = "Line",        icon = "line_plot",    packageId = "plot", commandId = "line-chart", description = "Visualize datasets as a a series of lines (with or without points)"),
      SpecialCommand(label = "Scatterplot", icon = "scatter_plot", packageId = "plot", commandId = "scatterplot", description = "Visualize datasets as a series of colored points"),
      SpecialCommand(label = "Map",         icon = "geo_plot",     packageId = "plot", commandId = "geo", description = "Plot a geospatial dataset on a map"),
    ),
    "Document" -> Seq(
      SpecialCommand(label = "Markdown", icon = "markdown", packageId = "docs", commandId = "markdown", description = "Document your project with markdown-formatted text"),
    ),
    "Export" -> Seq(
      SpecialCommand(label = "Dataframe", icon = "dump_table", packageId = "data", commandId = "unload", description = "Export a dataset to your local filesystem or a server"),
      SpecialCommand(label = "File",      icon = "dump_file",  packageId = "data", commandId = "unloadFile", description = "Export a raw file to your local filesystem or a server"),
    ),
  )

  val IS_DEFAULT:Set[(String, String)] = 
    DEFAULT.flatMap { _._2.map { _.tuple }}.toSet
}