package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import scala.scalajs.js
import rx._
import info.vizierdb.ui.serialized._
import info.vizierdb.ui.network._
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.rxExtras.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }

class Project(projectId: String)
             (implicit owner: Ctx.Owner, data: Ctx.Data)
{
  val properties = Var[Map[String, js.Dynamic]](Map.empty)
  val projectName = Rx { 
    properties().get("name")
                .map { _.toString}
                .getOrElse { "Untitled Project" } }

  val branches = Var[Map[String, BranchSummary]](Map.empty)
  val activeBranch = Var[Option[String]](None)

  def load(project: ProjectDescription): Project =
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

  activeBranch.trigger { 
    println("Triggering active branch change")
    branchSubscription.foreach { _.close() }
    branchSubscription = 
      activeBranch.now.map { branch =>
        new BranchSubscription(projectId, branch, Vizier.api)
      }
    workflow() = branchSubscription.map { new Workflow(_) }
  }

      // api.project("1")
      //    .onSuccess { case project =>
      //       project.defaultBranch
      //              .onSuccess { case branch =>
      //                 println(s"Branch: $branch")
      //                 val subscription = branch.subscribe
      //                 workflowView = new WorkflowView(subscription)
      //                 document.body.appendChild(workflowView.root)
      //              }
      //    }


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