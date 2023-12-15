/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
import scala.scalajs.js


class PythonSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  sealed trait PythonPackageEntry { 
    def root: dom.html.Element 
    def descriptor: serialized.PythonPackage
    def deleted: Boolean
    def requirementSpec: String
  }

  case class PythonPackage(pkg: serialized.PythonPackage, env: PythonEnvironmentEntry) extends PythonPackageEntry
  {
    val version = Var[Option[dom.html.Input]](None)
    var trashed = false

    val trashButton = 
      a(`class` := "fabutton",
            FontAwesome("trash"), href := "#", 
            onclick := { _:dom.Event => toggleTrash() }).render

    val upgradeButton =
      a(`class` := "fabutton",
            FontAwesome("level-up"), href := "#", 
            onclick := { _:dom.Event => toggleVersion() }).render

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

    def toggleVersion(): Unit =
      if(version.now.isEmpty){
        val field = input(`type` := "text", placeholder := "latest").render
        version() = Some(field)
        field.focus()
        upgradeButton.classList.add("depressed")
      } else {
        upgradeButton.classList.remove("depressed")
        version() = None
      }

    def selectedVersion =
      version.now.map { _.value match { 
                    case "" => None 
                    case x => Some(x)
                  } }
                 .getOrElse { pkg.version }

    def descriptor: serialized.PythonPackage = 
      serialized.PythonPackage(
        name = pkg.name,
        version = selectedVersion
      )

    def deleted = trashed

    def requirementSpec: String = 
      pkg.name ++ selectedVersion.map { "="+_ }.getOrElse { "" }

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
            _.getOrElse { 
              span(pkg.version, onclick := { _:dom.Event => toggleVersion() }).render 
            }
          }.reactive
        ),
        td(`class` := "actions",
          trashButton,
          upgradeButton,
        )
      ).render
  }

  case class NewPackage(env: PythonEnvironmentEntry) extends PythonPackageEntry
  {
    val name = input(`type` := "text", value := "beautifulsoup").render
    val version = input(`type` := "text", placeholder := "latest").render

    def trash(): Unit =
    {
      val me = env.packages.indexWhere { _ eq this }
      env.packages.remove(me)
    }

    def selectedVersion =
      version.value match { 
                case "" => None 
                case x => Some(x)
              }

    def requirementSpec: String = 
      name.value ++ selectedVersion.map { "="+_ }.getOrElse { "" }

    def descriptor: serialized.PythonPackage = 
      serialized.PythonPackage(
        name = name.value,
        version = version.value match {
          case "" => None
          case x => Some(x)
        }
      )

    def deleted = { name.value == "" }

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

    def requirements(): String =
    {
      packages.map { _.requirementSpec }.mkString("\n")
    }

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
      } else {
        Vizier.api.configUpdatePythonEnv(id.get, encoded)
      }.onSuccess { case descriptor =>
        dirty() = false
        this.id = Some(descriptor.id)
        packages.clear()
        packages.insertAll(0, descriptor.packages.map { PythonPackage(_, this) })
        load()
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
          // a(`class` := "fabutton",
          //   FontAwesome("files-o"), href := "#", 
          //   onclick := { _:dom.Event => println("duplicate") }),
          a(`class` := "fabutton",
            FontAwesome("share-square-o"), href := "#", 
            onclick := { _:dom.Event => 
              ShowModal(div(
                span("requirements.txt"),
                textarea(
                  rows := 20,
                  cols := 80,
                  requirements()
                )
              ).render)(button("Ok").render)
             }),
          a(`class` := "fabutton",
            FontAwesome("plus-circle"),
            onclick := { _:dom.Event => addPackage() }),

          // Save Button
          span(`class` := "save_button",
            dirty.map { 
              case false => span()
              case true => 
                button(`class` := "fabutton",
                  "Save ",
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
