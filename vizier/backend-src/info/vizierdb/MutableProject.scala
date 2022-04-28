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
package info.vizierdb

import play.api.libs.json._
import scalikejdbc._
import java.io.File
import java.net.URLConnection
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.util.Streams
import java.io.FileInputStream
import java.io.FileOutputStream
import info.vizierdb.spark.vizual.VizualCommand
import info.vizierdb.commands.vizual.{ Script => VizualScript }
import org.apache.spark.sql.types._
import org.apache.spark.sql.DataFrame
import info.vizierdb.spark.SparkSchema
import info.vizierdb.commands.data.DeclareParameters
import info.vizierdb.delta.WorkflowState
import info.vizierdb.delta.ComputeDelta
import info.vizierdb.spark.caveats.DataContainer
import info.vizierdb.serialized.Timestamps
import info.vizierdb.commands.FileArgument
import info.vizierdb.viztrails.Scheduler

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
   * The temporarily selected branch identifier
   */
  var branchId: Option[Identifier] = None

  /**
   * The current version of the [[Project]] represented by this mutable project
   */
  def project: Project = CatalogDB.withDBReadOnly { implicit s => Project.get(projectId) }

  /**
   * The current version of the active [[Branch]] being operated on by this mutable project
   */
  def branch: Branch = CatalogDB.withDBReadOnly { implicit s => activeBranch }

  /**
   * The current version of the active [[Branch]] being operated on by this mutable project
   * without a DB Session
   */
  def activeBranch(implicit session: DBSession) = 
    branchId.map { id => Branch.get(projectId = projectId, branchId = id) }
            .getOrElse { Project.activeBranchFor(projectId) }

  /**
   * The head [[Workflow]] being operated on by this mutable project
   */
  def head: Workflow = CatalogDB.withDBReadOnly { implicit s => activeBranch.head }

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
  def append(packageId: String, commandId: String, properties: Map[String, JsValue] = Map.empty)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, oldbranch.append(packageId, commandId, properties = JsObject(properties))(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
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
  def insert(position: Int, packageId: String, commandId: String, properties: Map[String, JsValue] = Map.empty)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, oldbranch.insert(position, packageId, commandId, properties = JsObject(properties))(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
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
  def update(position: Int, packageId: String, commandId: String, properties: Map[String, JsValue] = Map.empty)
            (args: (String, Any)*): (Branch, Workflow) =
  {
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, oldbranch.update(position, packageId, commandId, properties = JsObject(properties))(args:_*)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
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
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, oldbranch.freezeFrom(position)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
    return ret
  }

  /**
   * Update the branch head by invalidating every cell in the workflow and rerunning
   */
  def invalidateAllCells: (Branch, Workflow) =
  {
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, activeBranch.invalidate()) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
    return ret
  }

  /**
   * Update the branch head by invalidating every cell in the workflow and rerunning
   */
  def invalidate(cells: Iterable[Int]): (Branch, Workflow) =
  {
    if(cells.isEmpty){ 
      throw new IllegalArgumentException("Invalidating no cells")
    }
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, activeBranch.invalidate(cells.toSet)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
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
    val (oldbranch, ret) = CatalogDB.withDB { implicit s => 
      val oldbranch = activeBranch
      (oldbranch, oldbranch.thawUpto(position)) 
    }
    Scheduler.abort(oldbranch.headId)
    Scheduler.schedule(ret._2)
    return ret
  }

  /**
   * Block the calling thread until the current head workflow has completed execution.
   */
  def waitUntilReady
  {
    val workflowId = head.id
    Scheduler.joinWorkflow(workflowId, failIfNotRunning = false)
  }

  /**
   * Block the calling thread until the current head workflow has completed execution, and throw
   * an exception if the workflow execution encountered an error.
   */
  def waitUntilReadyAndThrowOnError
  {
    waitUntilReady
    val workflow = head
    CatalogDB.withDBReadOnly { implicit s => 
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
    CatalogDB.withDBReadOnly { implicit s => 
      activeBranch
             .head
             .cellByPosition(idx)
             .map { _.messages.toSeq }
    }
  }

  /**
   * Retrieve [[Timestamps]] resulting from cell processing for the head workflow
   */
  def timestamps: Seq[Timestamps] =
  {
    CatalogDB.withDBReadOnly { implicit s =>
      activeBranch.head
                  .cellsInOrder
                  .map { _.timestamps }
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
    waitForResult: Boolean = true,
    copyFile: Boolean = false
  ){
    append("data", "load")(
      "file" -> (
        if(copyFile){
          FileArgument( fileid = Some(addFile(file = new File(file), name = Some(name)).id) )
        } else {
          FileArgument( url = Some(file) )
        }
      ),
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
      CatalogDB.withDB { implicit s => 
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
    waitForResult: Boolean = true,
    properties: Map[String, JsValue] = Map.empty
  ) = 
  {
    append("script", language, properties = properties)("source" -> script)
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
    CatalogDB.withDBReadOnly { implicit s => 
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
    CatalogDB.withDBReadOnly { implicit s => 
      workflow.outputArtifacts
    }
  }
  /**
   * Retrieve [[ArtifactSummary]]s to all artifacts output by the entire workflow (the scope)
   * @return              A collection of [[ArtifactSummary]]s in the final cell's output scope.
   */
  def artifactSummaries: Map[String, ArtifactSummary] = 
  {
    val refs = artifactRefs
    CatalogDB.withDBReadOnly { implicit s => 
      refs.map { ref => ref.userFacingName -> ref.getSummary.get }
          .toMap
    }
  }

  /**
   * Retrieve the [[ArtifactRef]] for a specific name
   * @param  artifactName  The name of an artifact output by the workflow
   * @return               The [[ArtifactRef]] for the specified artifact
   */
  def artifactRef(artifactName: String): ArtifactRef =
    artifactRefs.find { _.userFacingName.equalsIgnoreCase(artifactName) }
            .getOrElse { 
              throw new VizierException(s"No Such Artifact $artifactName")
            }


  /**
   * Retrieve all [[Artifact]]s output by the entire workflow (the scope)
   * @return              A collection of [[Artifact]]s in the final cell's output scope.
   */
  def artifacts: Map[String, Artifact] =
  {
    val refs = artifactRefs
    CatalogDB.withDBReadOnly { implicit s => 
      refs.map { ref => ref.userFacingName -> ref.get.get }
    }.toMap
  }

  /**
   * Retrieve a specific [[Artifact]] in the output of the workflow
   * @param artifactName  The name of an artifact output by the workflow
   * @return              The [[Artifact]] identified by artifactName in the workflow's output
   */
  def artifact(artifactName: String): Artifact = 
  {
    val ref = artifactRef(artifactName)
    return CatalogDB.withDBReadOnly { implicit s => ref.get.get }
  }

  /**
   * Retrieve the spark dataframe corresponding to a specific artifact
   */
  def dataframe(artifactName: String): DataFrame =
  {
    val ref = artifactRef(artifactName)
    return CatalogDB.withDB { implicit s => ref.get.get.dataframe }
  }

  /**
   * Retrieve the spark dataframe corresponding to a specific artifact
   */
  def datasetData(artifactName: String): DataContainer =
  {
    val ref = artifactRef(artifactName)
    return CatalogDB.withDB { implicit s => ref.get.get.datasetData() }
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
        val df = CatalogDB.withDB { implicit s => artifact.dataframe }
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
    CatalogDB.withDBReadOnly { implicit s => ComputeDelta.getState(b) }
  }

  /**
   * Switch this [[MutableProject]] to a different branch without activating
   * the target branch.
   */
  def workWithBranch(branchId: Identifier): Unit =
  {
    this.branchId = Some(branchId)
  }

  /**
   * Find a branch with the specified name
   */
  def findBranch(nameOrBranchId: String): Option[Branch] =
  {
    nameOrBranchId match { 
      case MutableProject.NUMERIC(num) => 
        CatalogDB.withDBReadOnly { implicit s =>
          Branch.getOption(projectId, num.toLong)
        }
      case name => 
        CatalogDB.withDBReadOnly { implicit s =>
          Branch.withName(projectId, name)
        }
    }
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
    new MutableProject(CatalogDB.withDB { implicit s => Project.create(name).id })

  /**
   * Create a MutableProject for an existing project
   * @param projectId   The project to wrap with this MutableProject
   * @return            The constructed MutableProject
   */
  def apply(projectId: Identifier): MutableProject = new MutableProject(projectId)

  val NUMERIC = "^([0-9]+)$".r

  /**
   * Find a project for the provided name
   * @param project     The project name or identifier
   * @return            The constructed MutableProject
   */
  def find(nameOrProjectId: String): Option[MutableProject] =
  {
    nameOrProjectId match { 
      case NUMERIC(num) => Some(apply(num.toLong))
      case name => 
        CatalogDB.withDBReadOnly { implicit s =>
          Project.withName(name).map { _.id }.map { apply(_) }
        }
    }
  }
}

