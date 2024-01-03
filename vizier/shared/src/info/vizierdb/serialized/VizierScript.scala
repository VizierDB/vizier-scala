package info.vizierdb.serialized

import info.vizierdb.types.Identifier;
import info.vizierdb.nativeTypes.JsValue;
import play.api.libs.json.Format
import play.api.libs.json.Json

case class VizierScript(
  id: Identifier,
  version: Int,
  name: String,
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Identifier,
  modules: Seq[VizierScriptModule]
)

object VizierScript
{
  def fromWorkflow(projectId: Identifier, branchId: Identifier, workflow: WorkflowDescription, name: String = "Untitled"): VizierScript =
  {
    VizierScript(
      id = -1,
      version = -1,
      name = name,
      projectId = projectId,
      branchId = branchId,
      workflowId = workflow.id,
      modules = workflow.modules.map { 
        VizierScriptModule.Inline(_)
      }
    )
  }
}

sealed trait VizierScriptModule
{
  val enabled: Boolean
}

object VizierScriptModule
{
  val INLINE = "inline"

  case class Inline(
    spec: ModuleDescription,
    enabled: Boolean = true,
    `type`: String = VizierScriptModule.INLINE
  ) extends VizierScriptModule

}
