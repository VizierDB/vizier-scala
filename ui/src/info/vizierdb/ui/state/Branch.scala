package info.vizierdb.ui.state

import scala.scalajs.js
import info.vizierdb.ui.API
import info.vizierdb.types._
import scala.concurrent.Future

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

@js.native
trait BranchDescription extends BranchSummary
{
  val workflows: js.Array[WorkflowSummary] = js.native
}

// class BranchRef(summary: BranchSummary, projectId: Identifier, api: API)
// {
//   val id = summary.id
//   val createdAt = summary.createdAt
//   val lastModifiedAt = summary.lastModifiedAt
//   val sourceBranch = Option(summary.sourceBranch)
//   val sourceWorkflow = Option(summary.sourceWorkflow)
//   val sourceModule = Option(summary.sourceModule)
//   val isDefault = summary.isDefault
//   lazy val properties:Map[String, js.Dynamic] = 
//     summary.properties.values.map { v =>
//       v.key.toString -> v.value
//     }.toMap  
//   def name = 
//     properties.get("name")
//               .map { _.toString}
//               .getOrElse { "Untitled Branch" }
//   override def toString(): String = 
//     s"${this.getClass().getSimpleName()} $id (${name})"

//   def deref = api.branch(projectId, id)

//   def subscribe: BranchSubscription = 
//     new BranchSubscription(id, projectId, api)
// }

// class Branch(description: BranchDescription, projectId: Identifier, api: API)
//   extends BranchRef(description, projectId, api)
// {
//   val workflows = 
//     description.workflows
//                .map { new WorkflowRef(_, id, projectId, api)}
// }