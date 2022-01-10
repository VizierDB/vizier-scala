package info.vizierdb.viztrails

import scalikejdbc._
import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.commands.ExecutionContext
import info.vizierdb.delta.DeltaBus
import info.vizierdb.commands.Commands
import info.vizierdb.commands.Arguments
import java.util.concurrent.ForkJoinTask

class RunningCell(
  val cell: Cell, 
  val module: Module, 
  val scope: ScopeSummary, 
  workflowTask: RunningWorkflow
)
  extends ForkJoinTask[Result]
  with LazyLogging
{

  var result: Option[Result] = None

  def cleanup() = 
  {
    // Put code here to run in the main workflow thread
    logger.trace(s"Cleaning up after $cell")
  }

  def abort() =
  {
    cancel(true)
  }

  def exec(): Boolean =
  {
    try {
      result = Some(processSynchronously)
      return true
    } catch {
      case e: Throwable =>
        logger.error(s"A really serious internal error occurred: ${e.getMessage}")
        e.printStackTrace()
        return false
    } finally {
      logger.debug(s"Cell: $cell complete.  Signalling workflow")
      workflowTask.completionMessages.add(cell.position)
    }
  }

  /**
   * Translate the provided scope into a scope for an [[ExecutionContext]]
   */
  def inputArtifacts(implicit session: DBSession): Map[String, ArtifactSummary] =
  {
    // We can assume that we're not here unless the module has already been 
    // deemed to be runnable.  
    // 
    // Let's start by checking whether we need the complete scope
    val predictedProvenance =
      module.command.map { _.predictProvenance(module.arguments, module.properties) }
                    .getOrElse { ProvenancePrediction.default }

    if(predictedProvenance.openWorldReads){
      assert(scope.openWorldPrediction.equals(ArtifactDoesNotExist))
      logger.trace("Using all artifacts in scope")
      scope.allArtifactSummaries
    } else {
      logger.trace(s"Using only artifacts: ${predictedProvenance.reads}")
      scope.artifactSummariesFor(predictedProvenance.reads)
    }
  }

  def getRawResult(): Result = result.get
  def setRawResult(r: Result) { result = Some(r) }

  /**
   * Evaluate a single cell synchronously.
   *
   * @argument   cell     The cell to process
   * @return              The Result object for the evaluated cell
   */
  def processSynchronously: Result =
  {
    logger.info(s"Processing $cell")
    val (command, arguments, context, startedCell) =
      DB autoCommit { implicit session =>
        val (startedCell, result) = cell.start
        val workflow = startedCell.workflow
        val context = new ExecutionContext(
                            startedCell.projectId, 
                            inputArtifacts, 
                            startedCell.workflow, 
                            startedCell, 
                            module, 
                            { (mimeType, data) => 
                                DB.autoCommit { implicit s =>
                                  result.addMessage( 
                                    mimeType = mimeType, 
                                    data = data 
                                  )(s)
                                  DeltaBus.notifyMessage(
                                    workflow = workflow, 
                                    position = cell.position, 
                                    mimeType = mimeType, 
                                    data = data,
                                    stream = StreamType.STDOUT
                                  )(s)
                                }
                            },
                            { message =>
                                DB.autoCommit { implicit s =>
                                  result.addMessage( 
                                    message = message,
                                    stream = StreamType.STDERR
                                  )(s)
                                  DeltaBus.notifyMessage(
                                    workflow = workflow, 
                                    position = cell.position, 
                                    mimeType = "text/plain", 
                                    data = message.getBytes,
                                    stream = StreamType.STDERR
                                  )(s)
                                }
                            }
                          )
        val command = 
          module.command
                .getOrElse { 
                  context.error(s"Command ${module.packageId}.${module.commandId} does not exist")
                  return errorResult(startedCell)
                }
        val arguments = Arguments(module.arguments.as[Map[String, JsValue]], command.parameters)
        val argumentErrors = arguments.validate
        if(!argumentErrors.isEmpty){
          val msg = "Error in module arguments:\n"+argumentErrors.mkString("\n")
          logger.warn(msg)
          context.error(msg)
          return errorResult(startedCell)
        }
        DeltaBus.notifyStateChange(workflow, startedCell.position, startedCell.state)
        DeltaBus.notifyAdvanceResultId(workflow, startedCell.position, startedCell.resultId.get)
        /* return */ (command, arguments, context, startedCell)
      }
    logger.debug(s"About to run code for [${command.name}]($arguments) <- ($context)")

    try {
      command.process(arguments, context)
    } catch {
      case e:Exception => {
        e.printStackTrace()
        context.error(s"An internal error occurred: ${e.getMessage()}")
        return DB autoCommit { implicit session => 
          errorResult(startedCell)
        }
      }
    }
    return DB autoCommit { implicit session => 
      normalResult(startedCell, context)
    }
  }
  /**
   * Register an error result for the provided cell
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error messages
   * @return              The Result object for the cell
   */
  private def errorResult(cell: Cell)(implicit session: DBSession): Result = 
  {
    val result = cell.finish(ExecutionState.ERROR)._2
    val position = cell.position
    val workflow = cell.workflow

    val stateTransitions = 
      StateTransition.forAll( 
        sqls"position > ${cell.position}", 
        ExecutionState.PENDING_STATES -> ExecutionState.CANCELLED 
      )
    DeltaBus.notifyStateChange(workflow, cell.position, ExecutionState.ERROR)
    for(cell <- workflow.cellsWhere(sqls"""position > ${cell.position}""")) {
      DeltaBus.notifyStateChange(workflow, cell.position, ExecutionState.CANCELLED)
    }
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(
          c.state -> StateTransition.updateState(stateTransitions),
          c.resultId -> StateTransition.updateResult(stateTransitions)
        )
        .where.eq(c.workflowId, workflow.id)
          .and.gt(c.position, cell.position)
    }.update.apply()
    return result
  }
  /**
   * Register a execution result for the provided cell based on the provided execution context.
   * this may still trigger an error if one was registered during execution of the cell.
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error message
   */
  private def normalResult(cell: Cell, context: ExecutionContext)(implicit session: DBSession): Result = 
  {
    if(context.isError){ return errorResult(cell) }
    val result = cell.finish(ExecutionState.DONE)._2
    val workflow = cell.workflow

    for((userFacingName, identifier) <- context.inputs) {
      result.addInput( userFacingName, identifier )
    }
    for((userFacingName, artifact) <- context.outputs) {
      result.addOutput( userFacingName, artifact.map { _.id } )
      artifact match {
        case None => 
          DeltaBus.notifyDeleteArtifact(
            workflow = workflow,
            position = cell.position,
            name = userFacingName
          )
        case Some(actualArtifact) => 
          DeltaBus.notifyOutputArtifact(
            workflow = workflow, 
            position = cell.position, 
            name = userFacingName, 
            artifactId   = actualArtifact.id, 
            artifactType = actualArtifact.t
          )
      }
    }
    DeltaBus.notifyStateChange(cell.workflow, cell.position, ExecutionState.DONE)

    for(cell <- workflow.cellsWhere(sqls"position > ${cell.position}")){
      DeltaBus.notifyStateChange(
        workflow = workflow,
        position = cell.position,
        newState = cell.state
      )
    }
    return result
  }

}