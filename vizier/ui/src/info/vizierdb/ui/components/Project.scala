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
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.Vizier

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

  def refresh(andThen: => Unit): Unit =
  {
    println("Refreshing")
    api.projectGet(projectId)
       .onComplete {
        case Success(result) => println("Refreshed"); load(result); println("Loaded"); andThen
        case Failure(err) => Vizier.error(err.toString)
       }
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
    activeBranch() = Some(id)
  }

  def setActiveBranchName(name: String): Unit =
  {
    val branchId = activeBranch.now.get
    val branch = branches.now.get(branchId).get
    api.branchUpdate(projectId, branchId,
      serialized.PropertyList
                .toPropertyList(
                  serialized.PropertyList.toMap(
                    branch.properties
                  ) ++ Map("name" -> JsString(name))
                )
    ).onComplete { f => refresh() }
  }

  def branchActiveWorkflow(name: String): Unit =
  {
    val branchId = activeBranch.now.get
    api.branchCreate(projectId, 
      source = Some(serialized.BranchSource(
        branchId,
        workflowId = None, // Branch the workflow head
        moduleId = None,
      )),
      serialized.PropertyList
                .toPropertyList(
                  Map("name" -> JsString(name))
                )
    ).onComplete { 
      case Success(response) => refresh { setActiveBranch(response.id) }
      case Failure(error) => Vizier.error(error.toString())
    }
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

  val menu = new MenuBar(this)

  val root = 
    div(id := "project",
      menu.root,
      div(`class` := "content",
        div(
          `class` := "table_of_contents",
          tableOfContents.map { _.map { _.root } 
                                 .getOrElse { Spinner(size = 30):Frag } 
                              }.reactive
        ),
        div(
          `class` := "workflow",
          workflow.map { _.map { _.root }
                          .getOrElse { Spinner(size = 30):Frag } }.reactive
        )
      )
    ).render
}