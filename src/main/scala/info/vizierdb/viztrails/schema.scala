package info.vizierdb.viztrails

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{ Schema, KeyedEntity, Query }
import org.squeryl.dsl.{ OneToMany, ManyToOne, ManyToMany, CompositeKey2 }

import play.api.libs.json.{ Json, JsValue, JsObject }
import java.sql.Timestamp

import info.vizierdb.Types._
import info.vizierdb.viztrails.JsFieldImplicits._
import info.vizierdb.commands.{ Commands, Arguments }
import scala.languageFeature.existentials


/**
 * A vistrails project.  The project may have an optional set of user-defined properties.
 */
class Project(
  val id: Identifier,
  var name: String,
  var properties: JsonField,
  var activeBranchId: Identifier,
  val created: Timestamp,
  var modified: Timestamp
) 
  extends KeyedEntity[Identifier]
{
  lazy val branches: OneToMany[Branch] = 
    Viztrails.projectToBranches.left(this)

  def activeBranch: Branch = 
    Viztrails.branches.lookup(activeBranchId).get

  /**
   * Update the record directly in the database, along with the timestamp.
   */
  def save()
  {
    modified = Viztrails.now
    Viztrails.projects.update(this)
  }

  /**
   * Create a branch of the current workflow
   */
  def createBranch(
    name: String, 
    properties: JsObject = Json.obj(), 
    activate: Boolean = false,
    baseWorkflowId: Option[Identifier] = Some(activeBranch.headId)
  ) {
    inTransaction { 
      val now = Viztrails.now
      val branch = Viztrails.branches.insert(new Branch(
        0,        /* ID, this gets replaced */
        this.id,
        name, 
        properties, 
        -1,        /* Workflow ID.  This gets replaced by us a few lines later */
        now, 
        now
      ))

      baseWorkflowId match {
        case None => branch.initWorkflow()
        case Some(workflowId) => branch.cloneWorkflow(workflowId)
      }

      if(activate){ activeBranchId = branch.id; save() }

      /* return */ branch
    }
  }
}
object Project
{
  def apply(name: String, properties: JsObject = Json.obj()): Project = 
  {
    inTransaction { 
      val now = Viztrails.now
      val project = Viztrails.projects.insert(new Project(
        0,           /* Identifier.  Squeryll fixes this */
        name, 
        properties, 
        -1,          /* Active Branch.  We'll replace this with createBranch */
        now, 
        now
      ))
      project.createBranch("default", activate = true, baseWorkflowId = None)
      /* return */ project
    }
  }

}

/**
 * One branch of the project
 */
class Branch( 
  val id: Identifier,
  val projectId: Identifier,
  var name: String,
  var properties: JsonField,
  var headId: Identifier,
  val created: Timestamp,
  var modified: Timestamp
) 
  extends KeyedEntity[Identifier]
{
  lazy val project: ManyToOne[Project] = 
    Viztrails.projectToBranches.right(this)
  lazy val workflows: OneToMany[Workflow] = 
    Viztrails.branchToWorkflows.left(this)
  def head: Workflow = 
    Viztrails.workflows.lookup(headId).get

  private[viztrails] def initWorkflow(
    prevId: Option[Identifier] = None, 
    action: ActionType.T = ActionType.CREATE,
    actionModuleId: Option[Identifier] = None,
    setHead: Boolean = true
  ): Workflow = 
  {
    inTransaction {
      val now = Viztrails.now
      val workflow = Viztrails.workflows.insert(new Workflow(
        0,                 /* Identifier,  Squeryl replaces this */
        prevId,
        id,
        action,
        actionModuleId,
        Viztrails.now
      ))

      if(setHead) { headId = workflow.id; save() }
      /* return */ workflow
    }
  }
  private[viztrails] def cloneWorkflow(workflowId: Identifier) = 
  {
    inTransaction { 
      val workflow = initWorkflow(Some(workflowId))
      Viztrails.cells.insert(
        head.cells
            .map { cell => new Cell(
              workflow.id,
              cell.position,
              cell.moduleId,
              cell.resultId,
              cell.state
            )}
      )
      /* return */ workflow
    }
  }

  private def modify(
    module: Module, 
    action: ActionType.T,
    prevWorkflow: Identifier = headId,
    updatePosition: (Cell => Int) = { (existing: Cell) => existing.position },
    updateState: (Cell => ExecutionState.T) = { (existing: Cell) => existing.state },
    updateModuleId: (Cell => Identifier) = { (existing: Cell) => existing.moduleId },
    filterCells: (Cell => Boolean) = { (existing:Cell) => true },
    addModules: Iterable[(Identifier, Int)] = Seq()
  ): Workflow =
  {
    inTransaction { 
      val workflow = initWorkflow(
        prevId = Some(prevWorkflow),
        action = action,
        actionModuleId = Some(module.id),
        setHead = false
      )

      val cellsToInsert = 
        head.cells
            .filter { filterCells(_) }
            .map { cell => new Cell(
              workflow.id,
              updatePosition(cell),
              updateModuleId(cell),
              cell.resultId,
              updateState(cell)
            )}.toSeq ++ addModules.map { case (moduleId, position) =>
              new Cell(
                workflow.id,
                position,
                moduleId,
                None,
                ExecutionState.STALE
              )
            }
      Viztrails.cells.insert(cellsToInsert)

      headId = workflow.id; save()

      /* return */ workflow
    }
  }

  def insert(module: Module, position: Int): Workflow = 
    modify(
      module = module,
      action = ActionType.INSERT,
      updatePosition = 
        { (existing: Cell) => if(existing.position < position) { existing.position } 
                                      else { existing.position + 1 } },
      updateState = 
        { (existing: Cell) => if(existing.position < position) { existing.state } 
                                      else { ExecutionState.WAITING } },
      addModules = Seq(module.id -> position)
    )

  def append(module: Module): Workflow =
    inTransaction { modify( // inTransaction to link the head.length bit
      module = module,
      action = ActionType.APPEND,
      addModules = Seq(module.id -> head.length)
    )}

  def update(module: Module, position: Int): Workflow =
    modify(
      module = module,
      action = ActionType.UPDATE,
      addModules = Seq(module.id -> position),
      updateState = 
        { (existing: Cell) => if(existing.position < position) { existing.state } 
                                      else { ExecutionState.WAITING } },
      filterCells =
        { (existing: Cell) => existing.position != position }
    )

  def save()
  {
    modified = Viztrails.now
    Viztrails.branches.update(this)
  }

}

object ActionType extends Enumeration
{
  type T = Value

  val CREATE,
      APPEND,
      DELETE,
      INSERT,
      UPDATE = Value
}

/**
 * One version of a workflow.  
 */
class Workflow(
  val id: Identifier,
  val prevId: Option[Identifier],
  val branchId: Identifier,
  val action: ActionType.T,
  val actionModuleId: Option[Identifier],
  val created: Timestamp
) extends KeyedEntity[Identifier]
{
  lazy val branch: ManyToOne[Branch] =
    Viztrails.branchToWorkflows.right(this)
  lazy val modules: ManyToMany[Module, Cell] =
    Viztrails.cells.left(this)
  def modulesInOrder: Query[Module] =
    from(modules.associationMap) { case (m, c) => select(m).orderBy(c.position.asc) }
  def cells: Query[Cell] = 
    modules.associations
  def cellsInOrder: Query[Cell] = 
    from(cells) { c => select(c).orderBy(c.position.asc) }
  def prev: Option[Workflow] = 
    prevId.map { Viztrails.workflows.lookup(_).get }
  def length: Int =
    from(cells) { c => compute(max(c.position)) }.single.measures
      .map { _ + 1 } // length is max position + 1
      .getOrElse { 0 } // NULL means no cells

  def this() =
    this(0, Some(-1), 0, ActionType.CREATE, Some(-1), Viztrails.now)
}

object ExecutionState extends Enumeration
{
  type T = Value

  val DONE   = Value(1, "DONE")    /* The referenced execution is correct and up-to-date */
  val ERROR   = Value(2, "ERROR")    /* The cell or a cell preceding it is affected by a notebook 
                                        error */
  val WAITING = Value(3, "WAITING")  /* The referenced execution follows a stale cell and *may* be 
                                        out-of-date, depending on the outcome of the stale cell  */
  val BLOCKED = Value(4, "BLOCKED")  /* The referenced execution is incorrect for this workflow and 
                                        needs to be recomputed, but is blocked on another one */
  val STALE   = Value(5, "STALE")    /* The referenced execution is incorrect for this workflow and 
                                        needs to be recomputed */
}

class Cell(
  val workflowId: Identifier,
  val position: Int,
  var moduleId: Identifier,
  var resultId: Option[Identifier],
  var state: ExecutionState.T
) extends KeyedEntity[CompositeKey2[Identifier, Int]]
{
  def id = compositeKey(workflowId, position)

  def module: Module = 
    Viztrails.modules.lookup(moduleId).get
  def result: Option[Result] =
    resultId.map { Viztrails.results.lookup(_).get }
  def inputs: Seq[ArtifactReference] =
    result.toSeq.flatMap { _.inputs }
  def outputs: Seq[ArtifactReference] =
    result.toSeq.flatMap { _.outputs }
  def successors: Query[Cell] =
    from(Viztrails.cells) { c => where( (c.workflowId === workflowId ) 
                                    and (c.position gt position) )
                                  .select(c)
                                  .orderBy(c.position.asc) }

  def start: Result =
    inTransaction {
      val newResult = Viztrails.results.insert(new Result(0, Viztrails.now, None))
      resultId = Some(newResult.id)
      save()
      /* return */ newResult
    }

  def finish(newState: ExecutionState.T): Result =
    inTransaction {
      val newResult = result.getOrElse { start }
      newResult.finished = Some(Viztrails.now)
      newResult.save()
      state = newState
      save()
      /* return */ newResult
    }

  /**
   * Not useful as a constructor, but needed by Sqryll to get type information
   */
  def this() =
    this(0, 0, 0, Some(0), ExecutionState.DONE)

  def save() =
    Viztrails.cells.update(this)

  override def toString = s"Workflow $workflowId @ $position: Module $moduleId ($state)"
}

class Module(
  val id: Identifier,
  val packageId: String,
  val commandId: String,
  val arguments: JsonField,
  val description: String,
  val properties: JsonField,
  val revisionOfId: Option[Identifier] = None
) extends KeyedEntity[Identifier]
{
  def revisionOf: Option[Module] =
    revisionOfId.map { Viztrails.modules.lookup(_).get }

  def this() = 
    this(
      0, 
      "", 
      "", 
      new JsonField("{}".getBytes()),
      "BLANK COMMAND", 
      new JsonField("{}".getBytes()), 
      Some(0 )
    )

  override def toString(): String = 
    s"Module $id ${packageId}.${commandId}($arguments)"
}
object Module
{
  def apply(
    packageId: String, 
    commandId: String, 
    properties: JsObject = Json.obj(),
    revisionOfId: Option[Identifier] = None
  )(
    arguments: (String, Any)*
  ): Module =
  {
    val command = Commands.getOption(packageId, commandId)
                          .getOrElse {
                            throw new RuntimeException(s"Invalid Command ${packageId}.${commandId}")
                          }
    val encodedArguments = command.encodeArguments(arguments.toMap)

    inTransaction { 
      Viztrails.modules.insert(new Module(
        0,
        packageId,
        commandId,
        encodedArguments,
        command.format(encodedArguments),
        properties,
        revisionOfId
      ))
    }
  }
}

class Result(
  val id: Identifier,
  val started: Timestamp,
  var finished: Option[Timestamp],
) extends KeyedEntity[Identifier] 
{
  lazy val inputs: OneToMany[ArtifactReference] =
    Viztrails.resultToInputs.left(this)
  lazy val outputs: OneToMany[ArtifactReference] =
    Viztrails.resultToOutputs.left(this)
  lazy val consoleOutputs: OneToMany[LogEntry] =
    Viztrails.resultToLogEntries.left(this)

  def addOutput(userFacingName: String, artifactId: Identifier) =
    Viztrails.outputs.insert(new ArtifactReference(id, artifactId, userFacingName))
  def addInput(userFacingName: String, artifactId: Identifier) =
    Viztrails.inputs.insert(new ArtifactReference(id, artifactId, userFacingName))
  def addLogEntry(data: Array[Byte], mimeType: String): Unit =
    Viztrails.logEntries.insert(new LogEntry(0, id, mimeType, data))

  def save() =
    Viztrails.results.update(this)
}

object ArtifactType extends Enumeration
{
  type T = Value

  val DATASET  = Value(1, "Dataset")
  val FUNCTION = Value(2, "Function")
  val BLOB     = Value(3, "Blob")
}

class Artifact(
  val id: Identifier,
  val t: ArtifactType.T,
  val content: Array[Byte]
) extends KeyedEntity[Identifier]
{
  def nameInBackend = s"${t}_$id"

  def string = new String(content)
  val json = Json.toJson(content)

  def this() =
    this(0, ArtifactType.BLOB, Array[Byte]())
}
object Artifact
{
  def get(id: Identifier): Option[Artifact] = Viztrails.artifacts.lookup(id)
  def make(t: ArtifactType.T, content: Array[Byte]): Artifact = 
    Viztrails.artifacts.insert(new Artifact(0, t, content))
}

class ArtifactReference(
  val resultId: Identifier,
  val artifactId: Identifier,
  val userFacingName: String
) extends KeyedEntity[CompositeKey2[Identifier, String]]
{
  def id = compositeKey(resultId, userFacingName)

  def module: Module =
    Viztrails.modules.lookup(resultId).get

  def artifact: Artifact =
    Viztrails.artifacts.lookup(artifactId).get
}

class LogEntry(
  val id: Identifier,
  val resultId: Identifier,
  val mimeType: String,
  val data: Array[Byte]
) extends KeyedEntity[Identifier]
{
  def dataString = new String(data)
}
object Viztrails
  extends Schema
{

  val projects = table[Project]
  on(projects) { table => declare(
    table.id is autoIncremented,
  )}

  ////////////////////////////////////////////////

  val branches = table[Branch]
  on(branches) { table => declare(
    table.id is autoIncremented,
  )}
  val projectToBranches = 
    oneToManyRelation(projects, branches)
      .via( (project, branch) => branch.projectId === project.id )

  ////////////////////////////////////////////////

  val workflows = table[Workflow]
  on(workflows) { table => declare(
    table.id is autoIncremented,
  )}
  val branchToWorkflows = 
    oneToManyRelation(branches, workflows)
      .via( (branch, workflow) => workflow.branchId === branch.id )

  ////////////////////////////////////////////////

  val modules = table[Module]
  on(modules) { table => declare(
    table.id is autoIncremented,
  )}

  ////////////////////////////////////////////////

  val cells = 
    manyToManyRelation(workflows, modules)
      .via[Cell]{ (workflow, module, cell) => 
        ( cell.workflowId === workflow.id,
          cell.moduleId === module.id )
      }

  ////////////////////////////////////////////////

  val results = table[Result]
    on(results) { table => declare(
      table.id is autoIncremented
    )}
  val resultToCells = 
    oneToManyRelation(results, cells)
      .via{ (result, cell) => result.id === cell.resultId }

  ////////////////////////////////////////////////

  val artifacts = table[Artifact]
  on(artifacts) { table => declare(
    table.id is autoIncremented
  )}

  ////////////////////////////////////////////////

  val inputs = table[ArtifactReference]("Inputs")
  val resultToInputs =
    oneToManyRelation(results, inputs)
      .via { (result, input) => 
        result.id === input.resultId
      }

  ////////////////////////////////////////////////

  val outputs = table[ArtifactReference]("Outputs")
  val resultToOutputs =
    oneToManyRelation(results, outputs)
      .via { (result, output) => 
        result.id === output.resultId
      }

  ////////////////////////////////////////////////

  val logEntries = table[LogEntry]
  on(logEntries) { table => declare(
  )}
  val resultToLogEntries = 
    oneToManyRelation(results, logEntries)
      .via { (result, logEntry) => 
        result.id === logEntry.resultId
      }


  def now = new Timestamp(System.currentTimeMillis())
}