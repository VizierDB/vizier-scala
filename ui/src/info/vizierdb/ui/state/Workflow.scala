package info.vizierdb.ui.state

import scala.scalajs.js
import info.vizierdb.ui.API
import info.vizierdb.types._

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

class WorkflowRef(
  summary: WorkflowSummary, 
  branchId: Identifier, 
  projectId: Identifier,
  api: API
) 
{
  def id = summary.id
  def createdAt = summary.createdAt
  def action = summary.action
  def packageId = summary.packageId
  def commandId = summary.commandId
  def links = summary.links

  override def toString(): String = 
    s"${this.getClass().getSimpleName()} $id [$action" +
      packageId.map { " " + _ }.getOrElse { "" } +
      commandId.map { "." + _ }.getOrElse { "" } +
    "]"
}

class Workflow(
  description: WorkflowDescription,
  branchId: Identifier, 
  projectId: Identifier,
  api: API
) extends WorkflowRef(description, branchId, projectId, api)
{
  def state = description.state
  def modules = description.modules
  def datasets = description.datasets
  def dataobjects = description.dataobjects
  def readOnly = description.readOnly
  def tableOfContents = description.tableOfContents
}

