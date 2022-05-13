package info.vizierdb.ui.components

import rx._
import info.vizierdb.serialized
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.ModuleSubscription

class StaticWorkflow(workflow: serialized.WorkflowDescription)(implicit owner: Ctx.Owner)
{
  val moduleViews = 
    workflow.modules
  					.zipWithIndex
  					.map { case (mod, idx) => 
  						new Module(
  							new ModuleSubscription(mod, branch = null, idx),
  						)
  					}

  /**
   * The root DOM node of the workflow
   * 
   * Note.  This should closely mirrir [[Workflow]].  Any CSS-related changes applied here
   * should be propagated there as well.
   */
  val root = 
    div(`class` := "workflow_content",

    )
}