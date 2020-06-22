package info.vizierdb.viztrails

import play.api.libs.json.{ JsValue, JsObject }
import org.squeryl.PrimitiveTypeMode._

import info.vizierdb.Types._
import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import org.squeryl.dsl.GroupWithMeasures

object Scheduler
  extends LazyLogging
{

  def errorResult(cell: Cell, message: String) = {
    inTransaction {
      val result = cell.finish(ExecutionState.ERROR)
      val position = cell.position
      val workflowId = cell.workflowId
      result.addLogEntry(message.getBytes(), "text/plain")
      update(Viztrails.cells) { c =>
        where((c.workflowId === workflowId) and (c.position gt position))
          .set(c.state := ExecutionState.ERROR)
      }
    }
  }
  def successResult(cell: Cell, context: ExecutionContext) = 
  {
    inTransaction {
      val result = cell.finish(ExecutionState.DONE)
      for((userFacingName, identifier) <- context.inputs) {
        result.addInput( userFacingName, identifier )
      }
      for((userFacingName, artifact) <- context.outputs) {
        result.addOutput( userFacingName, artifact.id )
      }
      for((data, mimeType) <- context.logEntries) {
        result.addLogEntry( data, mimeType )
      }

      Provenance.updateSuccessorState(cell, 
        context.scope ++ context.outputs.mapValues { _.id }
      )
    }

  }

  def processSynchronously(cell: Cell)
  {
    logger.trace(s"Processing $cell")
    val (command, arguments, context) =
      inTransaction {
        val module = cell.module
        val command = 
          Commands.getOption(module.packageId, module.commandId)
                  .getOrElse { errorResult(cell, s"Command ${module.packageId}.${module.commandId} does not exist"); return }
        val scope = Provenance.getScope(cell)
        val context = new ExecutionContext(scope)
        val arguments = Arguments(module.arguments.as[Map[String, JsValue]], command.parameters)
        val argumentErrors = arguments.validate
        if(!argumentErrors.isEmpty){
          errorResult(cell, "Error in module arguments:\n"+argumentErrors.mkString("\n"))
        }
        val result = cell.start

        /* return */ (command, arguments, context)
      }

    try {
      command.process(arguments, context)
    } catch {
      case e:Exception => {
        e.printStackTrace()
        errorResult(cell, s"An internal error occurred: ${e.getMessage()}")
        return
      }
    }
    if(context.errorMessage.isEmpty){ 
      successResult(cell, context)
    } else { 
      errorResult(cell, context.errorMessage.get)
    }
  }

  def nextStaleCellForWorkflow(workflowId: Identifier): Option[Cell] = 
  {
    inTransaction {
      from(Viztrails.cells) { c => 
        where((c.state === ExecutionState.STALE) and (c.workflowId === workflowId))
          .select(c) 
          .orderBy(c.position.asc)
      }.headOption
    }
  }

  def processWorkflowUntilDone(workflowId: Identifier)
  {
    var curr = nextStaleCellForWorkflow(workflowId)
    while(curr != None){
      processSynchronously(curr.get)
      curr = nextStaleCellForWorkflow(workflowId)
    }
  }

  def allPendingWork: Seq[Cell] =
  {
    inTransaction {
      from(Viztrails.cells) { c => 
        where(c.state === ExecutionState.STALE)
          .groupBy(c.workflowId)
          .compute( max(c.position) )

      }.flatMap { c:GroupWithMeasures[Identifier, Option[Int]] => 
        c.measures.map { position =>
          Viztrails.cells.lookup(compositeKey(c.key, position)).get
        }
      }.toSeq
    }
  }

}