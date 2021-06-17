package info.vizierdb.ui.state

import scala.scalajs.js

import info.vizierdb.ui.API
import info.vizierdb.types._
import scala.concurrent.Future

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

@js.native
trait ProjectDescription extends ProjectSummary
{
  val branches: js.Array[BranchSummary] = js.native
}

// class ProjectRef(summary: ProjectSummary, api: API)
// {
//   lazy val id: Identifier = summary.id
//   def createdAt = summary.createdAt
//   def lastModifiedAt = summary.lastModifiedAt
//   lazy val defaultBranchId: Identifier = summary.defaultBranch
//   def links = summary.links
//   lazy val properties:Map[String, js.Dynamic] = 
//     summary.properties.values.map { v =>
//       v.key.toString -> v.value
//     }.toMap
//   def name = 
//     properties.get("name")
//               .map { _.toString}
//               .getOrElse { "Untitled Project" }
//   override def toString(): String = 
//     s"${this.getClass().getSimpleName()} $id (${name})"

//   def defaultBranch: Future[Branch] = api.branch(id, defaultBranchId)
// }
// class Project(description: ProjectDescription, api: API) 
//   extends ProjectRef(description, api)
// {
//   lazy val branches = 
//     description.branches
//                .map { summary => 
//                   new BranchRef(
//                     summary = summary,
//                     projectId = id, 
//                     api = api
//                   )
//                 }

// }

