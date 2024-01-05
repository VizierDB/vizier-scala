package info.vizierdb.serialized

import info.vizierdb.types.Identifier;
import info.vizierdb.nativeTypes.JsValue;
import play.api.libs.json.Format
import play.api.libs.json.Json
import info.vizierdb.types

case class VizierScript(
  id: Identifier,
  version: Long,
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
      modules = workflow.modules.map { module =>
        VizierScriptModule.Inline(module, enabled = {module.statev2 == types.ExecutionState.DONE})
      }
    )
  }
}

sealed trait VizierScriptModule
{
  def enabled: Boolean
  def id: Identifier

  def inputs: Set[String]
  def outputs: Map[String, types.ArtifactType.T]
}
object VizierScriptModule
{
  val INLINE = "inline"
  val INPUT_OUTPUT = "in_out"


  /**
   * A 'real' module corresponding to some module in the system
   * 
   * By convention, these module IDs are unique and correspond to the actual
   * module in the original workflow.
   */
  case class Inline(
    spec: ModuleDescription,
    enabled: Boolean = true,
    `type`: String = VizierScriptModule.INLINE
  ) extends VizierScriptModule
  {
    def id = spec.moduleId
    def inputs = spec.inputs.keySet
    def outputs = spec.artifacts.map { a => a.name -> a.t }.toMap
  }

  /**
   * A synthetic module that captures input/output state.
   * @param id       An unique identifier. By convention, these module IDs are negative.  
   * @param imports  Artifacts imported into the script from outside
   * @param exports  Artifacts exported from the script into the outside
   * @param enabled  Whether the cell is 
   * 
   */
  case class InputOutput(
    id: Identifier,
    imports: Map[String, types.ArtifactType.T],
    exports: Map[String, types.ArtifactType.T],
    `type`: String = VizierScriptModule.INPUT_OUTPUT
  ) extends VizierScriptModule
  {
    def inputs = exports.keySet
    def outputs = imports
    def enabled = true
  }
}
