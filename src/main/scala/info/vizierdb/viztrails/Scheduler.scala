package info.vizierdb.viztrails

import play.api.libs.json.{ JsValue, JsObject }
import scalikejdbc._
import java.util.concurrent.{ ForkJoinTask, ForkJoinPool }
import com.typesafe.scalalogging.LazyLogging

import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.catalog.binders._
import info.vizierdb.catalog.{ Workflow, Cell, Result }

object Scheduler
  extends LazyLogging
{
  val workers = new ForkJoinPool()
  val runningWorkflows = scala.collection.mutable.Map[Identifier,WorkflowExecution]()

  /**
   * Schedule a workflow for execution.  This should be automatically called from the branch
   * mutator operations.
   */
  def schedule(workflow: Workflow) 
  {
    logger.debug(s"Scheduling Workflow ${workflow.id}")
    this.synchronized {
      if(runningWorkflows contains workflow.id){
        logger.warn(s"Ignoring attempt to reschedule workflow ${workflow.id}")
        return
      }
      if(workflow.aborted){
        logger.warn(s"Ignoring attempt to schedule aborted workflow ${workflow.id}")
        return        
      }
      val executor = new WorkflowExecution(workflow)
      runningWorkflows.put(workflow.id, executor)
      workers.execute(executor)
    }
  }

  /**
   * Abort a (possibly) runnign workflow workflow.  This shouldn't be called directly.  Instead
   * use Workflow.abort or one of Branch's mutator operations.
   */
  def abort(workflowId: Identifier)
  {
    logger.debug(s"Trying to abort Workflow ${workflowId}")
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      logger.debug(s"Aborting Workflow ${workflowId}")
      if(!executor.isDone){ executor.cancel(true) }
      runningWorkflows.remove(workflowId)
    }
  }

  /**
   * Initialize the scheduler after a from-scratch startup, starting execution for all non-aborted
   * workflows with operations still pending.
   */
  def init(implicit session: DBSession)
  {
    logger.debug("Initalizing Viztrails scheduler")
    this.synchronized {
      sql"""
        SELECT workflow.* 
        FROM workflow,
             (SELECT DISTINCT workflowid 
              FROM cell WHERE state = ${ExecutionState.STALE.id}) stale_cells
        WHERE workflow.id = stale_cells.workflowid
        """
        .map { Workflow(_) }.list.apply()
        .foreach { schedule(_) }
    }
  }

  /**
   * Free resources associated with the specified workflow if they are no longer needed.
   */
  def cleanup(workflowId: Identifier)
  {
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      if(executor.isDone()) { runningWorkflows.remove(workflowId) }
    }
  }

  /**
   * Check to see if the specified workflow is still pending.  Aliased as Workflow.isPending
   */
  def isWorkflowPending(workflowId: Identifier): Boolean = 
    { cleanup(workflowId); this.synchronized { runningWorkflows contains workflowId } }

  /**
   * Block until the specified workflow completes.  The workflow must already be scheduled.
   *
   * In general, this method should only be used for testing.  
   */
  def joinWorkflow(workflowId: Identifier)
  {
    logger.debug(s"Trying to join with Workflow ${workflowId}")
    val executor = this.synchronized { 
      runningWorkflows.get(workflowId).getOrElse { 
        throw new RuntimeException(s"Workflow $workflowId is not running or has already been cleaned up") }
    }
    executor.join()
    cleanup(workflowId)
  }

  /**
   * Register an error result for the provided cell
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error message
   * @return              The Result object for the cell
   */
  private def errorResult(cell: Cell, message: String)(implicit session: DBSession): Result = {
    ???
    // catalogTransaction {
    //   val result = cell.finish(ExecutionState.ERROR)
    //   val position = cell.position
    //   val workflowId = cell.workflowId
    //   result.addLogEntry(message.getBytes(), "text/plain")
    //   update(Viztrails.cells) { c =>
    //     where((c.workflowId === workflowId) and (c.position gt position))
    //       .set(c.state := ExecutionState.ERROR,
    //            c.resultId := None)
    //   }
    //   result
    // }
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
    ???
    // if(context.isError){ errorResult(cell, context.errorMessage.get) }
    // else {
    //   catalogTransaction { 
    //     val result = cell.finish(ExecutionState.DONE)
    //     for((userFacingName, identifier) <- context.inputs) {
    //       result.addInput( userFacingName, identifier )
    //     }
    //     for((userFacingName, artifact) <- context.outputs) {
    //       result.addOutput( userFacingName, artifact.id )
    //     }
    //     for((data, mimeType) <- context.logEntries) {
    //       result.addLogEntry( data, mimeType )
    //     }

    //     Provenance.updateSuccessorState(cell, 
    //       context.scope ++ context.outputs.mapValues { _.id }
    //     )
    //     result
    //   }
    // }
  }

  /**
   * Evaluate a single cell synchronously.
   *
   * @argument   cell     The cell to process
   * @return              The Result object for the evaluated cell
   */
  def processSynchronously(cell: Cell): Result =
  {
    ???
    // logger.trace(s"Processing $cell")
    // val (command, arguments, context) =
    //   catalogTransaction {
    //     val module = cell.module
    //     val command = 
    //       Commands.getOption(module.packageId, module.commandId)
    //               .getOrElse { return errorResult(cell, s"Command ${module.packageId}.${module.commandId} does not exist"); }
    //     val scope = Provenance.getScope(cell)
    //     val context = new ExecutionContext(scope)
    //     val arguments = Arguments(module.arguments.as[Map[String, JsValue]], command.parameters)
    //     val argumentErrors = arguments.validate
    //     if(!argumentErrors.isEmpty){
    //       errorResult(cell, "Error in module arguments:\n"+argumentErrors.mkString("\n"))
    //     }
    //     val result = cell.start

    //     /* return */ (command, arguments, context)
    //   }

    // try {
    //   command.process(arguments, context)
    // } catch {
    //   case e:Exception => {
    //     e.printStackTrace()
    //     return errorResult(cell, s"An internal error occurred: ${e.getMessage()}")
    //   }
    // }
    // if(context.errorMessage.isEmpty){ 
    //   return normalResult(cell, context)
    // } else { 
    //   return errorResult(cell, context.errorMessage.get)
    // }
  }

  class WorkflowExecution(workflow: Workflow)
    extends ForkJoinTask[Unit]
  {
    var currentCellExecution = null

    // Workflow caches dependent cells, so this should be able to avoid going to the DB entirely
    def nextTarget: Option[Cell] = 
      DB readOnly { implicit session =>
        val c = Cell.syntax
        withSQL {
          select
            .from(Cell as c)
            .where.eq(c.state, ExecutionState.STALE)
            .orderBy(c.position)
            .limit(1)
        }.map { Cell(_) }.single.apply()
      }
      // catalogTransaction {
      //   workflow.cells.filter { cell => /* println(cell); */ cell.state == ExecutionState.STALE }
      //                 .toIterator
      //                 .foldLeft(None:Option[Cell]) { 
      //                   case (None, y) => Some(y)
      //                   case (Some(x), y) if y.position < x.position => Some(y)
      //                   case (Some(x), _) => Some(x)
      //                 }
      // }

    def exec(): Boolean = 
    {
      logger.debug(s"Starting processing of Workflow ${workflow.id}")
      var cell: Option[Cell] = nextTarget
      while( (!workflow.aborted) &&  (cell != None) ){
        processSynchronously(cell.get)
        cell = nextTarget
      }
      logger.debug(s"Done processing Workflow ${workflow.id}")
      return true
    }
    def setRawResult(x: Unit): Unit = {}
    def getRawResult(): Unit = {}
  }
}