/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
import info.vizierdb.VizierException
import org.mimirdb.vizual.{ Command => VizualCommand }
import info.vizierdb.commands.vizual.{ Script => VizualScript }
import org.apache.spark.sql.types.DataType
import org.mimirdb.spark.{ Schema => SparkSchema }

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
  def freezeFrom(position: Int): (Branch, Workflow) =
  {
    val (oldbranch, ret) = DB autoCommit { implicit s => 
      val oldbranch = Project.activeBranchFor(projectId)
      (oldbranch, oldbranch.freezeFrom(position)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2.id)
    return ret
  }
  def thawUpto(position: Int): (Branch, Workflow) =
  {
    val (oldbranch, ret) = DB autoCommit { implicit s => 
      val oldbranch = Project.activeBranchFor(projectId)
      (oldbranch, oldbranch.thawUpto(position)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2.id)
    return ret
  }

  def waitUntilReady
  {
    Scheduler.joinWorkflow(head.id, failIfNotRunning = false)
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
    inferTypes: Boolean = true,
    schema: Seq[(String, DataType)] = Seq.empty
  ){
    append("data", "load")(
      "file" -> file,
      "name" -> name,
      "loadFormat" -> format,
      "loadInferTypes" -> inferTypes,
      "loadDetectHeaders" -> true,
      "schema" -> 
        schema.map { case (name, dataType) => Map(
          "schema_column" -> name, 
          "schema_type" -> SparkSchema.encodeType(dataType)
        )}
    )
    waitUntilReadyAndThrowOnError
  }

  def script(script: String, language: String = "python") = 
  {
    append("script", language)("source" -> script)
    waitUntilReadyAndThrowOnError
  }

  def vizual(dataset: String, script: VizualCommand*) =
  {
    append("vizual", "script")(
      "dataset" -> dataset,
      "script" -> VizualScript.encode(script)
    )
    waitUntilReadyAndThrowOnError
  }
  def sql(script: String): Unit = sql(script -> null)
  def sql(scriptTarget: (String, String)): Unit =
  {
    append("sql", "query")(
      "source" -> scriptTarget._1,
      "output_dataset" -> Option(scriptTarget._2)
    )
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
    Streams.closeAfter(new FileInputStream(file)) { in => 
      Streams.closeAfter(new FileOutputStream(artifact.file)) { out => 
        Streams.cat(in, out)
      }
    }
    return artifact
  }

  def artifact(artifactName: String): Artifact = 
  {
    val ref = (
      artifactRefs.find { _.userFacingName.equalsIgnoreCase(artifactName) }
                  .getOrElse { 
                    throw new VizierException(s"No Such Artifact $artifactName")
                  }
    )
    return DB.readOnly { implicit s => ref.get.get }
  }
  def show(artifactName: String, rows: Integer = null): Unit =
  {

    val artifact: Artifact = this.artifact(artifactName)
    artifact.t match {
      case ArtifactType.DATASET => {
        import org.mimirdb.api.MimirAPI
        import org.mimirdb.caveats.implicits._
        MimirAPI.catalog
                .get(artifact.nameInBackend)
                .showCaveats(count = Option(rows).map { _.toInt }.getOrElse(20))
      }
      case _ => throw new VizierException(s"Show unsupported for ${artifact.t}")
    }
  }


}

object MutableProject
{
  def apply(name: String): MutableProject = 
    new MutableProject(DB.autoCommit { implicit s => Project.create(name).id })
  def apply(projectId: Identifier): MutableProject = new MutableProject(projectId)
}

