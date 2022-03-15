package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import scala.scalajs.js
import rx._
import scala.concurrent.{ Promise, Future }
import info.vizierdb.serialized
import info.vizierdb.ui.network.{ API, BranchSubscription }
import info.vizierdb.ui.rxExtras.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }
import info.vizierdb.util.Logging
import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.JsValue
import play.api.libs.json.JsString

class Project(val projectId: Identifier, val api: API, autosubscribe: Boolean = true)
             (implicit owner: Ctx.Owner)
  extends Object
  with Logging
{
  val properties = Var[Map[String, JsValue]](Map.empty)
  val projectName = Rx { 
    properties().get("name")
                .map { _.as[String] }
                .getOrElse { "Untitled Project" } }
  val branches = Var[Map[Identifier, serialized.BranchSummary]](Map.empty)
  val activeBranch = Var[Option[Identifier]](None)
  val activeBranchName = Rx {
    activeBranch() match {
      case None => "Unknown Branch"
      case Some(id) => branches().get(id)
                                 .map { _.name }
                                 .getOrElse("Unknown Branch")
    }
  }

  def load(project: serialized.ProjectDescription): Project =
  {
    assert(projectId == project.id)
    properties() = 
      project.properties.map { v =>
        v.key.toString -> v.value
      }.toMap
    branches() = 
      project.branches.map { b =>
        b.id -> b
      }.toMap
    if(activeBranch.now.isEmpty){
      activeBranch() = Some(project.defaultBranch)
    }
    return this
  }

  def setProjectName(name: String): Unit =
  {
    api.projectUpdate(projectId,
      serialized.PropertyList
                .toPropertyList(
                  properties.now ++ Map("name" -> JsString(name))
                )
    )
  }

  def setActiveBranch(id: Identifier): Unit =
  {
    api.projectUpdate(projectId,
      serialized.PropertyList
                .toPropertyList(
                  properties.now
                ),
      defaultBranch = Some(id)
    )
  }

  var branchSubscription: Option[BranchSubscription] = None
  val workflow = Var[Option[Workflow]](None) 
  val tableOfContents:Rx[Option[TableOfContents]] = 
    Rx { workflow().map { wf => new TableOfContents(wf) }}

  if(autosubscribe){
    activeBranch.trigger { 
      logger.info("Triggering active branch change")
      branchSubscription.foreach { _.close() }
      branchSubscription = 
        activeBranch.now.map { branch =>
          new BranchSubscription(this, branch, api)
        }
      workflow() = branchSubscription.map { new Workflow(_, this) }
    }
  }

  val root = 
    div(id := "project",
      tag("nav")(
        tableOfContents.map { _.map { _.root } 
                               .getOrElse { span("Loading....") } 
                            }.reactive
      ),
      workflow.map { _.map { _.root }
                      .getOrElse { span("Loading...") } }.reactive
    )
}