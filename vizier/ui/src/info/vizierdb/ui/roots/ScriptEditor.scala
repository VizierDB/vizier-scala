package info.vizierdb.ui.roots

import rx._
import info.vizierdb.ui.Vizier
import org.scalajs.dom.document
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.widgets.Spinner
import scala.concurrent.Future
import info.vizierdb.serialized.VizierScript
import scala.concurrent.ExecutionContext.Implicits.global

class ScriptEditor(script: VizierScript)
{

  val root = div("Hello").render
}

object ScriptEditor
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val scriptId = arguments.get("script").map { _.toLong }
    val branchId = arguments.get("branch").map { _.toLong }
    val workflowId = arguments.get("workflow").map { _.toLong }

    val editor = Var[Option[ScriptEditor]](None)

    var loading: Future[ScriptEditor] = 
      (scriptId, branchId, workflowId) match {
        case (_, Some(branchId), Some(workflowId)) => 
          Vizier.api.workflowGet(projectId, branchId, workflowId)
                .map { workflow => 
                  new ScriptEditor(VizierScript.fromWorkflow(projectId, branchId, workflow))
                }
        case _ => Vizier.error("No script provided")
      }

    loading.onSuccess { case ed => editor() = Some(ed) }

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>

      val root = div(`class` := "script_editor", 
                      editor.map { 
                        _.map { _.root }
                         .getOrElse { Spinner(50) }
                      }.reactive
                    )

      document.body.appendChild(root)
      OnMount.trigger(document.body)
    })
  }
}