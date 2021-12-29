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
import info.vizierdb.spark.vizual.VizualCommand
import info.vizierdb.commands.vizual.{ Script => VizualScript }
import org.apache.spark.sql.types._
import info.vizierdb.spark.SparkSchema
import info.vizierdb.commands.data.DeclareParameters
import info.vizierdb.delta.WorkflowState
import info.vizierdb.delta.ComputeDelta

/**
 * Convenient wrapper class around the Project class that allows mutable access to the project and
 * its dependencies.  Generally, this class should only be used for testing or interactive 
 * exploration on the scala console.
 */
class MutableProject(
  var projectId: Identifier
)
{
  /**
   * The current version of the [[Project]] represented by this mutable project
   */
  def project: Project = DB readOnly { implicit s => Project.get(projectId) }

  /**
   * The current version of the active [[Branch]] being operated on by this mutable project
   */
  def branch: Branch = DB readOnly { implicit s => Project.activeBranchFor(projectId) }

  /**
   * The head [[Workflow]] being operated on by this mutable project
   */
  def head: Workflow = DB readOnly { implicit s => Project.activeHeadFor(projectId) }

  /**
   * Append a new command to the end of the workflow.  The existing workflow will be aborted
   * (if running) and the new workflow will be executed asynchronously.
   * 
   * @param    packageId      The packageId of the command to add
   * @param    commandId      The commandId of the command to add
   * @param    args           A list of argument name -> value pairs in Scala-native format
   * @return                  A 2-tuple of the Branch and Workflow created by the update
   * 
   * Example:
   * <pre>
   * project.append("vizual", "insertColumn")(
   *    dataset -> "my_dataset"
   *    name -> "My Column",
   *    dataType -> "int"
   * )
   * </pre>
   */
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

  /**
   * Insert a new command somewhere into the workflow.  The existing workflow will be aborted
   * (if running) and the new workflow will be executed asynchronously.
   * 
   * @param    position       The 0-numbered position where the inserted cell should be
   * @param    packageId      The packageId of the command to add
   * @param    commandId      The commandId of the command to add
   * @param    args           A list of argument name -> value pairs in Scala-native format
   * @return                  A 2-tuple of the Branch and Workflow created by the update
   * 
   * Example:
   * <pre>
   * project.insert(23, "vizual", "insertColumn")(
   *    dataset -> "my_dataset"
   *    name -> "My Column",
   *    dataType -> "int"
   * )
   * </pre>
   */
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

  /**
   * Replace an existing cell in the workflow with a new command.  The existing workflow will be 
   * aborted (if running) and the new workflow will be executed asynchronously.
   * 
   * @param    position       The 0-numbered position of the cell to replace
   * @param    packageId      The packageId of the command to add
   * @param    commandId      The commandId of the command to add
   * @param    args           A list of argument name -> value pairs in Scala-native format
   * @return                  A 2-tuple of the Branch and Workflow created by the update
   * 
   * Example:
   * <pre>
   * project.update(23, "vizual", "insertColumn")(
   *    dataset -> "my_dataset"
   *    name -> "My Column",
   *    dataType -> "int"
   * )
   * </pre>
   */
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

  /**
   * Freeze all cells starting from a given position.  The existing workflow will be aborted
   * (if running) and the new workflow will be executed asynchronously (typically a no-op).
   * 
   * @param    position       The 0-numbered position of the first cell to freeze
   * @return                  A 2-tuple of the Branch and Workflow created by the update
   */
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

  /**
   * Thaw all cells up to and including a given position.  The existing workflow will be aborted
   * (if running) and the new workflow will be executed asynchronously.
   * 
   * @param    position       The 0-numbered position of the last cell to thaw
   * @return                  A 2-tuple of the Branch and Workflow created by the update
   */
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

  /**
   * Block the calling thread until the current head workflow has completed execution.
   */
  def waitUntilReady
  {
    Scheduler.joinWorkflow(head.id, failIfNotRunning = false)
  }

  /**
   * Block the calling thread until the current head workflow has completed execution, and throw
   * an exception if the workflow execution encountered an error.
   */
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

  /**
   * Retrieve the messages produced by the cell at a specified position
   * @param   idx        The position of a cell
   * @return             A sequence of [[Message]]s produced by the cell at this position or 
   *                     None if there is no cell at this position
   */
  def apply(idx: Int): Option[Seq[Message]] =
  {
    DB.readOnly { implicit s => 
      Project.activeHeadFor(projectId)
             .cellByPosition(idx)
             .map { _.messages.toSeq }
    }
  }

  /**
   * Load a dataset into this mutable project (shorthand for <tt>append("data", "load")(_)</tt>)
   * @param file           The file to load (ok to use relative file paths)
   * @param name           The name of the dataset to create
   * @param format         The file format (default: "csv"); See [[LoadDataset]]
   * @param inferTypes     If true, the loading process will attempt to guess the type for all
   *                       string columns loaded by spark.  (default: true)
   * @param schema         Override the schema inferred by the data loader (default: use the 
   *                       data loader's schema)
   * @param waitForResult  If true, the load is processed synchronously (default: true)
   */
  def load(
    file: String, 
    name: String, 
    format: String="csv", 
    inferTypes: Boolean = true,
    schema: Seq[(String, DataType)] = Seq.empty,
    waitForResult: Boolean = true
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
    if(waitForResult) { waitUntilReadyAndThrowOnError }
  }
  
  /**
   * Import a file into this mutable project
   * @param file           The file to import (ok to use relative file paths)
   * @param name           The name of the dataset to create (default: use the filename)
   * @param mimetype       The MIME type of the file
   * @return               The [[Artifact]] of the imported file.
   */
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
      Streams.closeAfter(new FileOutputStream(artifact.absoluteFile)) { out => 
        Streams.cat(in, out)
      }
    }
    return artifact
  }

  /**
   * Execute a script (shorthand for <tt>append("script", _)(_)</tt>)
   * @param script         The text of the script
   * @param language       The scripting language to use (default: python)
   * @param waitForResult  If true, the script is evaluated synchronously (default: true)
   */
  def script(
    script: String, 
    language: String = "python", 
    waitForResult: Boolean = true
  ) = 
  {
    append("script", language)("source" -> script)
    if(waitForResult) { waitUntilReadyAndThrowOnError }
  }

  /**
   * Execute a vizual script (shorthand for <tt>append("vizual", "script")(_)</tt>)
   * @param dataset        The dataset to transform
   * @param script         One or more vizual commands
   * 
   * The vizual script is evaluated synchronously.
   */
  def vizual(dataset: String, script: VizualCommand*) =
  {
    append("vizual", "script")(
      "dataset" -> dataset,
      "script" -> VizualScript.encode(script)
    )
    waitUntilReadyAndThrowOnError
  }

  /**
   * Execute a sql query (shorthand for <tt>append("sql", "query")(_)</tt>)
   * @param script         The text of the SQL query
   * 
   * The sql query is evaluated synchronously.
   */
  def sql(script: String): Unit = sql(script -> null)

  /**
   * Execute a sql query (shorthand for <tt>append("sql", "query")(_)</tt>)
   * @param scriptTarget   A pair query -> target table
   * 
   * Example
   * <pre>
   * project.sql("SELECT * FROM R" -> "my_result")
   * </pre>
   * 
   * The sql query is evaluated synchronously.
   */
  def sql(scriptTarget: (String, String)): Unit =
  {
    append("sql", "query")(
      "source" -> scriptTarget._1,
      "output_dataset" -> Option(scriptTarget._2)
    )
    waitUntilReadyAndThrowOnError
  }

  /**
   * Set one or more parameter artifacts (shorthand for <tt>append("data", "parameters")(_)</tt>)
   * @param params         One or more parameter -> value pairs (in scala native formats)
   * 
   * The parameter update cell is evaluated synchronously.
   */
  def setParameters(params: (String, Any)*)
  {
    append("data", "parameters")(
      DeclareParameters.PARAM_LIST -> 
        params.map { case (k, v) => 
          val (t, s) = v match {
            case s: String     => StringType -> s
            case i: Integer    => IntegerType -> i.toString
            case f: Float      => FloatType -> f.toString
            case d: Double     => DoubleType -> d.toString
          }
          Map(
            DeclareParameters.PARAM_NAME -> k,
            DeclareParameters.PARAM_VALUE -> s,
            DeclareParameters.PARAM_TYPE -> SparkSchema.encodeType(t)
          )
        }
    )
    waitUntilReadyAndThrowOnError
  }

  /**
   * Retrieve the messages produced by the last cell in the workflow as a sequence of [[Message]]s
   */
  def lastOutput =
  {
    val workflow = head
    DB.readOnly { implicit s => 
      workflow.cells.reverse.head.messages
    }
  }

  /**
   * Retrieve the messages produced by the last cell in the workflow as a string
   */
  def lastOutputString =
    lastOutput.map { _.dataString }.mkString("\n")

  /**
   * Retrieve [[ArtifactRef]]erences to all artifacts output by the entire workflow (the scope)
   * @return              A collection of [[ArtifactRef]]s in the final cell's output scope.
   */
  def artifactRefs: Seq[ArtifactRef] = 
  {
    val workflow = head
    DB.readOnly { implicit s => 
      workflow.outputArtifacts
    }
  }

  /**
   * Retrieve all [[Artifact]]s output by the entire workflow (the scope)
   * @return              A collection of [[Artifact]]s in the final cell's output scope.
   */
  def artifacts: Seq[Artifact] =
  {
    val refs = artifactRefs
    DB.readOnly { implicit s => 
      refs.map { _.get.get }
    }    
  }

  /**
   * Retrieve a specific [[Artifact]] in the output of the workflow
   * @param artifactName  The name of an artifact output by the workflow
   * @return              The [[Artifact]] identified by artifactName in the workflow's output
   */
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

  /**
   * Print a specific [[Artifact]] to the console to standard out
   * 
   * @param artifactName  The name of an artifact output by the workflow
   * @param rows          The number of rows to output if the artifact is a dataset
   * 
   * This function returns nothing... it just prints.
   */
  def show(artifactName: String, rows: Integer = null): Unit =
  {
    val artifact: Artifact = this.artifact(artifactName)
    artifact.t match {
      case ArtifactType.DATASET => {
        import org.mimirdb.caveats.implicits._
        val df = DB.autoCommit { implicit s => artifact.dataframe }
        df.showCaveats(count = Option(rows).map { _.toInt }.getOrElse(20))
      }
      case _ => throw new VizierException(s"Show unsupported for ${artifact.t}")
    }
  }

  /**
   * Obtain a snapshot of the current workflow for use with [[ComputeDelta]]
   */
  def snapshot: WorkflowState =
  {
    val b = branch
    DB.readOnly { implicit s => ComputeDelta.getState(b) }
  }
}

/**
 * Convenient wrapper class around the Project class that allows mutable access to the project and
 * its dependencies.  Generally, this class should only be used for testing or interactive 
 * exploration on the scala console.
 */
object MutableProject
{
  /**
   * Create a brand new project and return a MutableProject for it
   * @param name        The name to assign the new project
   * @return            The constructed MutableProject
   */
  def apply(name: String): MutableProject = 
    new MutableProject(DB.autoCommit { implicit s => Project.create(name).id })

  /**
   * Create a MutableProject for an existing project
   * @param projectId   The project to wrap with this MutableProject
   * @return            The constructed MutableProject
   */
  def apply(projectId: Identifier): MutableProject = new MutableProject(projectId)
}

