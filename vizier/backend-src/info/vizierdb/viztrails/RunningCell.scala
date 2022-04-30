package info.vizierdb.viztrails

import scalikejdbc._
import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.commands.ExecutionContext
import info.vizierdb.delta.{ DeltaBus, DeltaOutputArtifact }
import info.vizierdb.commands.Commands
import info.vizierdb.commands.Arguments
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import info.vizierdb.api.FormattedError
import info.vizierdb.VizierException
import info.vizierdb.vega.DateTime
import java.time.ZonedDateTime

class RunningCell(
  val cell: Cell, 
  val module: Module, 
  val scope: ScopeSummary, 
  workflowTask: RunningWorkflow
)
  extends ForkJoinTask[Result]
  with LazyLogging
{

  class CellCancelled() extends Exception

  var result: Option[Result] = None
  var startedCell: Cell = null
  var aborted = new AtomicBoolean(false)

  def cleanup() = 
  {
    aborted.set(true)
    // Put code here to run in the main workflow thread
    logger.trace(s"Cleaning up after $cell")
  }

  def abort() =
  {
    try {
      aborted.set(true)
      // Under some circumstances this.cancel will not actually abort the running
      // task.  If that happens, it returns false;  and we're now responsible for
      // handling task cl
      if(!this.cancel(true)){
        logger.warn(s"The JVM refuses to abort the currently running cell $this;  The cancellation will be registered now and vizier-visible effects will be suppressed, although the cell will likely continue running in the background")
      }
      CatalogDB.withDB { implicit session => 
        errorResult(startedCell, ExecutionState.CANCELLED)
      }
    } finally {
      logger.debug(s"Cell: $cell aborted.  Signalling workflow")
      if(!aborted.get){
        workflowTask.completionMessages.add(cell.position)
      }
    }
  }

  def exec(): Boolean =
  {
    try {
      processSynchronously match {
        case _ if aborted.get => 
          CatalogDB.withDB { implicit session => 
            errorResult(startedCell, ExecutionState.CANCELLED)
          }
        case Success(context) =>
          CatalogDB.withDB { implicit session => 
            logger.trace(s"Emitting normal result for cell $this")
            normalResult(startedCell, context)
          }
        case Failure(exc) => 
          CatalogDB.withDB { implicit session => 
            errorResult(startedCell)
          }
      }
      return true
    } catch {
      case e: Throwable =>
        logger.error(s"A really serious internal error occurred: ${e.getMessage}")
        e.printStackTrace()
        return false
    } finally {
      logger.debug(s"Cell: $cell complete.  Signalling workflow")
      if(!aborted.get){
        workflowTask.completionMessages.add(cell.position)
      }
    }
  }

  /**
   * Translate the provided scope into a scope for an [[ExecutionContext]]
   */
  def inputArtifacts(implicit session: DBSession): Map[String, Artifact] =
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
      scope.allArtifacts
    } else {
      logger.trace(s"Using only artifacts: ${predictedProvenance.reads}")
      scope.artifactsFor(predictedProvenance.reads)
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
  def processSynchronously: Try[ExecutionContext] =
  {
    logger.info(s"Processing $cell")
    val (command, arguments, context) =
      CatalogDB.withDB { implicit session =>
        val (startedCellTemp, result) = cell.start
        startedCell = startedCellTemp
        val workflow = startedCell.workflow
        val context = new ExecutionContext(
                            startedCell.projectId, 
                            inputArtifacts, 
                            startedCell.workflow, 
                            startedCell, 
                            module, 
                            { (mimeType, data) => 
                                if(!aborted.get){
                                  CatalogDB.withDB { implicit s =>
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
                                }
                            },
                            { message =>
                                if(!aborted.get){
                                  CatalogDB.withDB { implicit s =>
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
                            }
                          )
        val command = 
          module.command
                .getOrElse { 
                  context.error(s"Command ${module.packageId}.${module.commandId} does not exist")
                  return return Failure(new VizierException("Module does not exist"))
                }
        val arguments = Arguments(module.arguments.as[Map[String, JsValue]], command.parameters)
        val argumentErrors = arguments.validate
        if(!argumentErrors.isEmpty){
          val msg = "Error in module arguments:\n"+argumentErrors.mkString("\n")
          logger.warn(msg)
          context.error(msg)
          return Failure(new VizierException(msg))
        }
        DeltaBus.notifyStateChange(workflow, startedCell.position, startedCell.state, startedCell.timestamps)
        DeltaBus.notifyAdvanceResultId(workflow, startedCell.position, startedCell.resultId.get)
        /* return */ (command, arguments, context)
      }
    logger.debug(s"About to run code for [${command.name}]($arguments) <- ($context)")
    try {
      command.process(arguments, context)
    } catch {
      case e:Exception => {
        e.printStackTrace()
        context.error(s"An internal error occurred: ${e.getMessage()}")
        return Failure(e)
      }
    }
    return Success(context)
  }
  /**
   * Register an error result for the provided cell
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error messages
   * @return              The Result object for the cell
   */
  private def errorResult(
    cell: Cell, 
    targetState: ExecutionState.T = ExecutionState.ERROR
  )(implicit session: DBSession): Result = 
  {
    val result = cell.finish(targetState)._2
    val position = cell.position
    val workflow = cell.workflow

    val stateTransitions = 
      StateTransition.forAll( 
        sqls"position > ${cell.position}", 
        ExecutionState.PENDING_STATES -> ExecutionState.CANCELLED 
      )
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
    DeltaBus.notifyStateChange(workflow, cell.position, targetState, cell.timestamps)
    for(cell <- workflow.cellsWhere(sqls"""position > ${cell.position}""")) {
      DeltaBus.notifyStateChange(workflow, cell.position, ExecutionState.CANCELLED, cell.timestamps)
    }
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
    }
    DeltaBus.notifyUpdateOutputs(
      workflow = workflow,
      position = cell.position,
      outputs = context.outputs.map { 
        case (name, None) => DeltaOutputArtifact.fromDeletion(name)
        case (name, Some(a)) => DeltaOutputArtifact.fromArtifact(a.summarize(name))
      }.toSeq
    )
    DeltaBus.notifyStateChange(cell.workflow, cell.position, ExecutionState.DONE, cell.timestamps)

    for(cell <- workflow.cellsWhere(sqls"position > ${cell.position}")){
      DeltaBus.notifyStateChange(
        workflow = workflow,
        position = cell.position,
        newState = cell.state,
        newTimestamps = cell.timestamps
      )
    }
    return result
  }

  override def toString(): String = 
    s"Task { $cell }"

}
