package info.vizierdb.viztrails

import scalikejdbc._
import info.vizierdb.Vizier
import info.vizierdb.catalog._
import info.vizierdb.types._
/**
 * Convenient wrapper class around the Project class that allows mutable access to the project and
 * its dependencies.  Generally, this class should only be used for testing or interactive 
 * exploration on the scala console.
 */
class MutableProject(
  var projectId: Identifier
)
{
  def project: Project = DB readOnly { implicit s => Project.get(projectId) }
  def branch: Branch = DB readOnly { implicit s => Project.activeBranchFor(projectId) }
  def head: Workflow = DB readOnly { implicit s => Project.activeHeadFor(projectId) }

  def append(packageId: String, commandId: String)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = DB autoCommit { implicit s => 
      val oldbranch = Project.activeBranchFor(projectId)
      (oldbranch, oldbranch.append(packageId, commandId)(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2.id)
    return ret
  }
  def insert(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = DB autoCommit { implicit s => 
      val oldbranch = Project.activeBranchFor(projectId)
      (oldbranch, oldbranch.insert(position, packageId, commandId)(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2.id)
    return ret
  }
  def update(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = DB autoCommit { implicit s => 
      val oldbranch = Project.activeBranchFor(projectId)
      (oldbranch, oldbranch.update(position, packageId, commandId)(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2.id)
    return ret
  }

  def waitUntilReady
  {
    Scheduler.joinWorkflow(head.id)
  }
}

object MutableProject
{
  def apply(name: String): MutableProject = new MutableProject(Vizier.createProject(name).id)
  def apply(projectId: Identifier): MutableProject = new MutableProject(projectId)
}