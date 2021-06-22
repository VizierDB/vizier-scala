package info.vizierdb.ui.network

import scala.scalajs.js
import info.vizierdb.ui.API
import info.vizierdb.types.StreamType

/**
 * Details about the server
 */
@js.native
trait ServiceDescriptor extends js.Object
{
  val name: String = js.native
  val createdAt: String = js.native
  val defaults: js.Dictionary[js.JSStringOps] = js.native
  val environment: EnvironmentDescriptor = js.native
  val links: js.Dictionary[js.JSStringOps] = js.native
}

/**
 * Details about Vizier on the server
 */
@js.native
trait EnvironmentDescriptor extends js.Object
{
  val name: String = js.native
  val version: String = js.native
  val backend: String = js.native
  val packages: js.Array[PackageDescriptor] = js.native
}

/**
 * Details about a Vizier package
 */
@js.native
trait PackageDescriptor extends js.Object
{
  val category: String = js.native
  val id: String = js.native
  val name: String = js.native
  val commands: js.Array[CommandDescriptor] = js.native
}

/**
 * Details about a Vizier command
 */
@js.native
trait CommandDescriptor extends js.Object
{
  val id: String = js.native
  val name: String = js.native
  val parameters: js.Array[ParameterDescriptor] = js.native
  val suggest: Boolean = js.native
}

/**
 * Details about a Vizier command parameter (typically subclassed)
 */
@js.native
trait ParameterDescriptor extends js.Object
{
  val index: Int = js.native
  val id: String = js.native
  val name: String = js.native
  val datatype: String = js.native
  val hidden: Boolean = js.native
  val required: Boolean = js.native
  val parent: js.UndefOr[String] = js.native
  val language: js.UndefOr[String] = js.native
  var elements: js.UndefOr[js.Array[ParameterDescriptor]] = js.native
}

/**
 * Details about a project
 */
@js.native
trait ProjectSummary extends js.Object
{
  val id: String = js.native
  val createdAt: String = js.native
  val lastModifiedAt: String = js.native
  val defaultBranch: String = js.native
  val properties: js.Array[js.Dynamic] = js.native
  val links: js.Dictionary[js.JSStringOps] = js.native
}

/**
 * An extended version of the [[ProjectSummary]] that also includes summaries of
 * all of the branches in the project.
 */
@js.native
trait ProjectDescription extends ProjectSummary
{
  val branches: js.Array[BranchSummary] = js.native
}

/**
 * Details about a Branch.
 */
@js.native
trait BranchSummary extends js.Object
{
  val id: String = js.native
  val createdAt: String = js.native
  val lastModifiedAt: String = js.native
  val sourceBranch: js.JSNumberOps = js.native
  val sourceWorkflow: js.JSNumberOps = js.native
  val sourceModule: js.JSNumberOps = js.native
  val isDefault: Boolean = js.native
  val properties: js.Array[js.Dynamic] = js.native
}

/**
 * An extended version of the [[BranchSummary]] object that also includes summaries of 
 * all of the workflows in the branch
 */
@js.native
trait BranchDescription extends BranchSummary
{
  val workflows: js.Array[WorkflowSummary] = js.native
}

/**
 * Details about a Workflow
 */
@js.native
trait WorkflowSummary extends js.Object
{
  val id: String = js.native
  val createdAt: String = js.native
  val action: String = js.native
  val packageId: js.UndefOr[String] = js.native
  val commandId: js.UndefOr[String] = js.native
  val links: js.Dictionary[String] = js.native
}

/**
 * An extended version of the workflow object that also include a list of modules
 * in the workflow, as well as data elements produced as outputs
 */
@js.native
trait WorkflowDescription extends WorkflowSummary
{
  val state: Int
  val modules: js.Array[ModuleDescription]
  val datasets: js.Array[js.Dynamic]
  val dataobjects: js.Array[js.Dynamic]
  val readOnly: Boolean
  val tableOfContents: js.Array[js.Dynamic]
}

/**
 * A description of a command embedded in a module
 */
@js.native
trait CommandDescription extends js.Object
{
  val packageId: String = js.native
  val commandId: String = js.native
  // val arguments: js.Array[js.Dictionary[js.Dynamic]] = js.native
}

/**
 * The output associated with a module
 */
@js.native
trait ModuleOutputDescription extends js.Object
{
  val stdout: js.Array[MessageDescription] = js.native
  val stderr: js.Array[MessageDescription] = js.native
}

/**
 * An dataset artifact column
 */
@js.native
trait DatasetColumn extends js.Object
{
  val id: Int = js.native
  val name: String = js.native
  val `type`: String = js.native

}

/**
 * A dataset artifact reference
 */
@js.native
trait DatasetSummary extends ArtifactSummary
{
  val columns: js.Array[DatasetColumn] = js.native
}

/**
 * An artifact reference
 */
@js.native
trait ArtifactSummary extends js.Object
{
  val id: js.UndefOr[Int] = js.native
  val name: String = js.native
  val category: js.UndefOr[String] = js.native
  val objType: js.UndefOr[String] = js.native
}

/**
 * The content of a module.
 */
@js.native
trait ModuleDescription extends js.Object
{
  val id: String = js.native
  val state: Int = js.native
  val statev2: Int = js.native
  val command: CommandDescription = js.native
  val text: String = js.native
  val links: js.Dictionary[js.Dynamic] = js.native
  val outputs: ModuleOutputDescription = js.native
  val artifacts: js.Array[ArtifactSummary] = js.native
}

/**
 * A message emitted by a cell.
 */
@js.native
trait MessageDescription extends js.Object
{
  val `type`: String = js.native
  val value: js.Dynamic = js.native
}

/**
 * A [[MessageDescription]] plus the stream it was associated with
 */
class StreamedMessage(description: MessageDescription, val stream: StreamType.T)
{
  def t = description.`type`
  def value = description.value
}