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
import info.vizierdb.types._


class PythonSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  sealed trait PythonPackageEntry { 
    def root: dom.html.Element 
    def descriptor: serialized.PythonPackage
    def deleted: Boolean
  }

  case class PythonPackage(pkg: serialized.PythonPackage, env: PythonEnvironmentEntry) extends PythonPackageEntry
  {
    val version = Var[Option[dom.html.Input]](None)
    var trashed = false

    val trashButton = 
      a(`class` := "fabutton",
            FontAwesome("trash"), href := "#", 
            onclick := { _:dom.Event => toggleTrash() }).render

    def toggleTrash(): Unit =
    {
      env.touch()
      trashed = !trashed
      if(trashed){ 
        trashButton.classList.add("depressed")
        root.classList.add("deleted")
      } else {
        trashButton.classList.remove("depressed")
        root.classList.remove("deleted")        
      }
    }

    def descriptor: serialized.PythonPackage = 
      serialized.PythonPackage(
        name = pkg.name,
        version = version.now
                         .map { _.value match { 
                            case "" => None 
                            case x => Some(x)
                          } }
                         .getOrElse { pkg.version }
      )

    def deleted = trashed

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
          trashButton,
          a(`class` := "fabutton",
            FontAwesome("level-up"), href := "#", 
            onclick := { _:dom.Event => println(s"upgrade $name") }),
        )
      ).render
  }

  case class NewPackage(env: PythonEnvironmentEntry) extends PythonPackageEntry
  {
    val name = input(`type` := "text").render
    val version = input(`type` := "text", placeholder := "latest").render

    def trash(): Unit =
    {
      val me = env.packages.indexWhere { _ eq this }
      env.packages.remove(me)
    }

    def descriptor: serialized.PythonPackage = 
      serialized.PythonPackage(
        name = name.value,
        version = version.value match {
          case "" => None
          case x => Some(x)
        }
      )

    def deleted = false

    val root = 
      tr(`class` := "tentative_package",
        td(name),
        td(version),
        td(`class` := "actions",
          a(`class` := "fabutton",
            FontAwesome("trash"), href := "#", 
            onclick := { _:dom.Event => trash() }),
        )        
      ).render
  }

  case class PythonEnvironmentEntry(name: String, var id: Option[Identifier], version: String)
  {
    val dirty = Var(false)
    def initial = id.isEmpty

    val packages = 
      RxBuffer[PythonPackageEntry]()

    val list_id = s"packages_for_$name"

    val packagesView = 
      RxBufferView(tbody().render, packages.rxMap { _.root })

    def loadPackages(): Unit =
    {
      if(id.isDefined){
        Vizier.api.configGetPythonEnv(id.get)
              .onComplete { 
                case Success(desc) => 
                  packages.clear()
                  packages.insertAll(0, desc.packages.map { PythonPackage(_, this) })
                case Failure(err) => 
                  err.printStackTrace()
                  Vizier.error(err.getMessage())
              }
      }
    }

    def touch(): Unit = 
    {
      dirty() = true
    }

    def addPackage(): Unit =
    {
      touch()
      expander.open()
      packages.insert(0, NewPackage(this))
    }

    def encoded: serialized.PythonEnvironmentDescriptor =
      serialized.PythonEnvironmentDescriptor(
        id = id.getOrElse(-1),
        name = name,
        pythonVersion = version,
        revision = -1,
        packages = packages.filter { !_.deleted } 
                           .map { _.descriptor }
      )
    def save(): Unit =
    {
      if(!dirty.now){ return }

      if(initial){
        Vizier.api.configCreatePythonEnv(name, encoded)
              .onComplete {
                case Success(id)  => 
                  dirty() = false
                  this.id = Some(id)
                  load()
                case Failure(err) =>
                  err.printStackTrace()
                  Vizier.error(err.getMessage()) 
              }
      }
    }

    val expander = Expander(list_id)

    val root = 
      div(`class` := "environment",

        // Title and Summary in the First row 
        div(`class` := "summary",
          expander.root,               // The 'arrow' to expand the package list
          span(
            span(`class` := "label", name),
            span(`class` := "details", 
              " (",
              version,
              ")"
            ),
            onclick := { e:dom.Event => expander.toggle(); e.stopPropagation() }
          ),
          span(`class` := "spacer"),

          // Action items in the first row
          a(`class` := "fabutton",
            FontAwesome("files-o"), href := "#", 
            onclick := { _:dom.Event => println("duplicate") }),
          a(`class` := "fabutton",
            FontAwesome("share-square-o"), href := "#", 
            onclick := { _:dom.Event => println("export") }),
          a(`class` := "fabutton",
            FontAwesome("plus-circle"),
            onclick := { _:dom.Event => addPackage() }),

          // Save Button
          span(`class` := "save_button",
            dirty.map { 
              case false => span()
              case true => 
                a(`class` := "fabutton",
                  FontAwesome("check-circle"), href := "#", 
                  onclick := { _:dom.Event => save() }),
            }.reactive,
          )
        ),

        // Follow-up package list
        div(`class` := "packages closed",
          attr("id") := list_id,
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
        )
      ).render
  }

  val environments = 
    RxBuffer[PythonEnvironmentEntry]()

  val environmentsView = 
    RxBufferView(div().render, environments.rxMap { _.root })

  var availableVersions = Var[Seq[String]](Seq.empty)

  def load(): Unit = 
  {
    Vizier.api.configListPythonEnvs()
              .onComplete { 
                case Success(settings) => 
                  availableVersions() = settings.versions
                  val newEnvironments = settings.environments.map { e => e.id -> e }.toMap
                  val newEnvSet = newEnvironments.keySet
                  val envSet = environments.flatMap { _.id }.toSet

                  val deletedEnvs: Set[Identifier] = envSet -- newEnvSet
                  val addedEnvs: Set[Identifier] = newEnvSet -- envSet

                  for(d <- deletedEnvs){
                    val idx = environments.indexWhere { _.id == Some(d) }

                    // Only delete the environment outright if it's not being
                    // used.  
                    if(!environments(idx).dirty.now){
                      environments.remove(idx)
                    // If the environment is "dirty" (i.e., the user was editing)
                    // the environment and someone deletes it out from under them
                    // then save it as a "new" environment instead.
                    } else {
                      environments(idx).id == None
                    }
                  }
                  environments.appendAll(
                    addedEnvs.map { env: Identifier =>
                      val node = PythonEnvironmentEntry(
                        name = newEnvironments(env).name,
                        id = Some(env), 
                        version = newEnvironments(env).pythonVersion
                      )
                      node.loadPackages()
                      node
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
            placeholder := "environment_name",
            pattern := "[a-zA-Z][a-zA-Z0-9]",
            attr("title") := "Alphanumeric, starting with a letter"
          ).render
    val defaultVersion = 
      environments.find(_.name == "System").get.version
    val newEnvVersion: dom.html.Input = 
      input(`type` := "text", 
            list := "python_versions", 
            placeholder := defaultVersion,
            onchange := { e:dom.Event =>
              val i = e.target.asInstanceOf[dom.html.Input]
              i.value match {
                case "" => ()
                case x if availableVersions.now.contains(x) => ()
                case _ => i.value = ""
              }
            }).render
    ShowModal.confirm(
      div("Name?"),
      div(newEnvName),
      div(newEnvVersion)
    ){ 
      val v = newEnvVersion.value match {
        case "" => defaultVersion
        case x if availableVersions.now.contains(x) => x
        case _ => defaultVersion
      }
      val env = PythonEnvironmentEntry(
                    name = newEnvName.value, 
                    id = None, 
                    version = v
                  )
      env.touch()
      environments.append(env)
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
    ),
    availableVersions.map { versions =>
      datalist(
        id := "python_versions",
        versions.map { v => option(value := v) }
      ),
    }.reactive,
  ).render

}
