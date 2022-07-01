package info.vizierdb.ui.components

import rx._
import info.vizierdb.serialized
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.types._

class StaticWorkflow(projectId: Identifier, workflow: serialized.WorkflowDescription)(implicit owner: Ctx.Owner)
{
  val modules: Seq[(serialized.ModuleDescription, Module)] = 
    workflow.modules
  					.zipWithIndex
  					.map { case (moduleDescription, idx) => 
              (
                moduleDescription, 
    						new Module(
    							new ModuleSubscription(moduleDescription, Right(this), Var(idx))
    						)
              )
  					}

  val artifacts = 
    modules.foldLeft(Map[String, (serialized.ArtifactSummary, Module)]()) { 
      case (accum, (moduleDescription, module)) =>
        accum ++ moduleDescription.artifacts.map { artifact =>
          (artifact.name, (artifact, module))
        }.toMap
    }

  val tableOfContents =
    new TableOfContents(
      projectId = projectId,
      modules = RxBuffer.ofSeq(modules.map { _._2 }),
      Var[Map[String, (serialized.ArtifactSummary, Module)]](artifacts)
    )
  /**
   * The root DOM node of the workflow
   * 
   * Note.  This should closely mirrir [[Workflow]] and [[Project]].  Any CSS-related changes applied here
   * should be propagated there as well.
   */
  val root = 
    div(id := "project",
      div(`class` := "content",
        div(
          `class` := "table_of_contents",
          tableOfContents.root
        ),
        div(
          `class` := "workflow",
          div(`class` := "workflow_content",
            div(`class` := "module_list",
              modules.map { _._2.root }
            ), 
          )
        )
      )
    ).render

}