package info.vizierdb.viztrails

import play.api.libs.json._

import scalikejdbc._
import java.io.File
import java.net.URLConnection
import info.vizierdb.Vizier
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.util.Streams
import java.io.FileInputStream
import java.io.FileOutputStream

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

  def waitUntilReadyAndThrowOnError
  {
    waitUntilReady
    val workflow = head
    DB.readOnly { implicit s => 
      for(cell <- workflow.cellsInOrder){
        if(cell.state == ExecutionState.ERROR) {
          throw new RuntimeException(
            cell.messages
                .filter { _.mimeType.equals(MIME.TEXT) }
                .map { _.dataString }
                .mkString("\n")
          )
        }
      }
    }
  }

  def apply(idx: Int): Option[Seq[Message]] =
  {
    DB.readOnly { implicit s => 
      Project.activeHeadFor(projectId)
             .cellByPosition(idx)
             .map { _.messages.toSeq }
    }
  }

  def load(
    file: String, 
    name: String, 
    format: String="csv", 
    inferTypes: Boolean = true
  ){
    append("data", "load")(
      "file" -> file,
      "name" -> name,
      "loadFormat" -> format,
      "loadInferTypes" -> inferTypes,
      "loadDetectHeaders" -> true
    )
    waitUntilReadyAndThrowOnError
  }

  def script(script: String, language: String = "python") = 
  {
    append("script", "python")("source" -> script)
    waitUntilReadyAndThrowOnError
  }
  def lastOutput =
  {
    val workflow = head
    DB.readOnly { implicit s => 
      workflow.cells.reverse.head.messages
    }
  }
  def lastOutputString =
    lastOutput.map { _.dataString }.mkString

  def artifactRefs: Seq[ArtifactRef] = 
  {
    val workflow = head
    DB.readOnly { implicit s => 
      workflow.outputArtifacts
    }
  }

  def artifacts: Seq[Artifact] =
  {
    val refs = artifactRefs
    DB.readOnly { implicit s => 
      refs.map { _.get.get }
    }    
  }

  def addFile(
    file: File, 
    name: Option[String] = None,
    mimetype: Option[String] = None
  ): Artifact = 
  {
    val realName = name.getOrElse { file.getName() }
    val realMimetype = URLConnection.guessContentTypeFromName(realName)
    val artifact = 
      DB.autoCommit { implicit s => 
        Artifact.make(
          projectId = projectId,
          ArtifactType.FILE,
          realMimetype,
          Json.obj(
            "filename" -> realName
          ).toString.getBytes
        )
      }
    Streams.cat(
      new FileInputStream(file),
      new FileOutputStream(artifact.file)
    )
    return artifact
  }

}

object MutableProject
{
  def apply(name: String): MutableProject = 
    new MutableProject(DB.autoCommit { implicit s => Project.create(name).id })
  def apply(projectId: Identifier): MutableProject = new MutableProject(projectId)
}