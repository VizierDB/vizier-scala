package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import scala.scalajs.js
import rx._
import scala.concurrent.{ Promise, Future }
import info.vizierdb.serialized
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.API
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }
import info.vizierdb.util.Logging
import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.JsValue

class Project(val projectId: Identifier, val api: API, autosubscribe: Boolean = true)
             (implicit owner: Ctx.Owner)
  extends Object
  with Logging
{
  val properties = Var[Map[String, JsValue]](Map.empty)
  val projectName = Rx { 
    properties().get("name")
                .map { _.toString}
                .getOrElse { "Untitled Project" } }

  val branches = Var[Map[Identifier, serialized.BranchSummary]](Map.empty)
  val activeBranch = Var[Option[Identifier]](None)

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

  var branchSubscription: Option[BranchSubscription] = None
  val workflow = Var[Option[Workflow]](None) 

  if(autosubscribe){
    activeBranch.trigger { 
      logger.info("Triggering active branch change")
      branchSubscription.foreach { _.close() }
      branchSubscription = 
        activeBranch.now.map { branch =>
          new BranchSubscription(projectId, branch, api)
        }
      workflow() = branchSubscription.map { new Workflow(_, this) }
    }
  }

  val root = 
    div(id := "project",
      h2(id := "name",
        Rx { projectName }
      ),
      div(id := "branches",
        h3("Branches"),
        ul( 
          Rx {
            branches.map { _.map { case (_, branch) => 
              li(
                span(branch.properties
                           .find { x => x.key.equals("name") }
                           .map { _.value.toString:String }
                           .getOrElse { "Unnamed Branch" }:String
                ),
                span(
                  { 
                    if(activeBranch().isDefined && 
                        activeBranch().get == branch.id){
                      " (active)"
                    } else { "" }
                  }
                )
              )
            }.toSeq }
          }
        )
      ),
      tag("nav")(
        h3("Table of Contents"),
        div(id := "table_of_contents", `class` := "contents"),
        "Table of Contents goes here"
      ),
      div(id := "workflow", 
        h3("Workflow"),
        Rx { workflow.map { _.map { _.root }
                             .getOrElse { span("Loading...") } } }
      )
    )
}