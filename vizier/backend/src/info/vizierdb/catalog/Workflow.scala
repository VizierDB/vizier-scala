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
package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import java.time.format.DateTimeFormatter
import info.vizierdb.VizierAPI
import info.vizierdb.serialized
import info.vizierdb.delta.{ UpdateCell, DeltaBus }
import info.vizierdb.viztrails.{ StateTransition }
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.TimerUtils

/**
 * One version of a workflow.  
 *
 * The workflow and its cells are mostly immutable once created with one exception.  The aborted
 * field should preserve a monotonicity guarantee (False -> True)
 */
case class Workflow(
  id: Identifier,
  prevId: Option[Identifier],
  branchId: Identifier,
  action: ActionType.T,
  actionModuleId: Option[Identifier],
  created: ZonedDateTime,
  aborted: Boolean
)
  extends LazyLogging
  with TimerUtils
{
  def projectId(implicit session: DBSession) = 
    Branch.get(branchId).projectId

  def cells(implicit session: DBSession): Seq[Cell] = 
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, id)
    }.map { Cell(_) }.list.apply()
  def cellsInOrder(implicit session: DBSession): Seq[Cell] = 
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, id)
        .orderBy(c.position)
    }.map { Cell(_) }.list.apply()
  def modules(implicit session: DBSession): Seq[Module] = 
    withSQL {
      val c = Cell.syntax
      val m = Module.syntax
      select(m.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
    }.map { Module(_) }.list.apply()
  def modulesInOrder(implicit session: DBSession): Seq[Module] = 
    withSQL {
      val c = Cell.syntax
      val m = Module.syntax
      select(m.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
        .orderBy(c.position)
    }.map { Module(_) }.list.apply()
  def cellsWhere(condition: SQLSyntax)(implicit session: DBSession): Seq[Cell] =
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, id)
          .and(Some(condition))
    }.map { Cell(_) }.list.apply()
  def cellsAndModulesInOrder(implicit session: DBSession): Seq[(Cell, Module)] =
    withSQL {
      val c = Cell.syntax
      val m = Module.syntax
      select(c.resultAll, m.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
        .orderBy(c.position)
    }.map { rs => (Cell(rs), Module(rs)) }
     .list.apply()
  def cellsModulesAndResultsInOrder(implicit session: DBSession): Seq[(Cell, Module, Option[Result])] =
  {
    val c = Cell.syntax
    val m = Module.syntax
    val r = Result.syntax
    withSQL {
      select(c.resultAll, m.resultAll, r.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .leftJoin(Result as r)
        .on(c.resultId, r.id)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
        .orderBy(c.position)
    }.map { rs => 
        (
          Cell(rs), 
          Module(rs),
          (if(rs.getOpt[Identifier](r.resultName.id).isDefined){ Some(Result(rs)) } else { None })
        )
      }
     .list.apply()
  }
     
  def cellByPosition(position: Int)(implicit session: DBSession): Option[Cell] =
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.position, position)
          .and.eq(c.workflowId, id)
    }.map { Cell(_) }.single.apply()

  def lastCell(implicit session: DBSession): Option[Cell] =
    withSQL {
      val c1 = Cell.syntax
      val c2 = Cell.syntax
      select
        .from(Cell as c1)
        .where.eq(c1.position, 
          sqls"""(SELECT max(${c2.position})
                  FROM ${Cell as c2}
                  WHERE ${c2.workflowId} = $id)""")
          .and.eq(c1.workflowId, id)
    }.map { Cell(_) }.single.apply()


  def cellByModuleId(moduleId: Identifier)(implicit session: DBSession): Option[Cell] =
    {
      withSQL {
        val c = Cell.syntax
        select(c.resultAll)
          .from(Cell as c)
          .where.eq(c.workflowId, id)
            .and.eq(c.moduleId, moduleId)
      }.map { Cell(_) }.single.apply()
    }

  def length(implicit session: DBSession): Int = Workflow.getLength(id)
  def abortIfNeeded(implicit session:DBSession): Workflow =
  {
    Workflow.abortIfNeeded(branchId = branchId, workflowId = id)
    copy(aborted = true)
  }
  def abort(implicit session:DBSession): Workflow =
  {
    Workflow.abort(branchId = branchId, workflowId = id)
    copy(aborted = true)
  }

  def outputArtifacts(implicit session: DBSession): Map[String,Artifact] =
  {
    OutputArtifactRef.outputArtifactsForWorkflow(id)
                     .toSeq
                     .sortBy { _._1 }
                     .map { _._2 }
                     .foldLeft(Map[String,Option[Artifact]]()) { _ ++ _ }
                     .collect { case (name, Some(artifact)) => name -> artifact}
                     .toMap
  }

  def allArtifacts(implicit session: DBSession): Seq[ArtifactRef] =
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    withSQL {
      select(o.resultAll)
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .where.eq(c.resultId, o.resultId)
          .and.eq(c.workflowId, id)
          .and.isNotNull(o.artifactId)
        .orderBy(c.position)
    }.map { OutputArtifactRef(_) }
     .list.apply()
  }

  def isRunning(implicit session: DBSession): Boolean =
    withSQL {
      val c = Cell.syntax
      select( c.resultAll )
        .from(Cell as c)
        .where.in(c.state, ExecutionState.PENDING_STATES.toSeq.map { _.id } )
          .and.eq(c.workflowId, id)
    }.map { _ => 1 }.list.apply().size > 0

  def describe(implicit session: DBSession): () => serialized.WorkflowDescription = 
  {
    val branch = Branch.get(branchId)
    val cellsModulesAndResults = cellsModulesAndResultsInOrder

    val messagesByCell = Message.messagesForWorkflow(id)
    val inputsByCell:Map[Cell.Position, Map[String, Identifier]] = 
      InputArtifactRef.inputArtifactIdsForWorkflow(id)
    val outputsByCell:Map[Cell.Position, Map[String, Option[Artifact]]] = 
      OutputArtifactRef.outputArtifactsForWorkflow(id)

    val summary = makeSummary(branch, actionModuleId.map { Module.get(_) })

    val state = cellsModulesAndResults.foldLeft(ExecutionState.DONE) { (prev, curr) =>
      if(!prev.equals(ExecutionState.DONE)){ prev }
      else { curr._1.state }
    }

    val modules = logTime("Workflow.describe/Module.describeAll") {
      cellsModulesAndResults.map { case (cell, module, result) =>
        module.describe(
          cell = cell,
          messages = messagesByCell.getOrElse(cell.position, Seq.empty),
          result = result,
          outputs = outputsByCell.getOrElse(cell.position, Map.empty).toSeq,
          inputs = inputsByCell.getOrElse(cell.position, Map.empty).toSeq,
          projectId = branch.projectId,
          branchId = branch.id,
          workflowId = id
        )
      }
    }
    val artifacts: Map[String, Artifact] = 
      cellsModulesAndResults.foldLeft(
        Map[String,Option[Artifact]]()
      ) { 
        case (scope:Map[String,Option[Artifact]], (cell, _, _)) =>
          val current:Map[String, Option[Artifact]] =
            outputsByCell.getOrElse(cell.position, Map.empty) 
          scope ++ current
      }
      .filter { _._2.isDefined }
      .mapValues { _.get }

    () => 
      summary.toDescription(
        state = state,
        modules = modules.map { _() },
        artifacts   = artifacts.toSeq.map { case (name, summ) => summ.summarize(name) },
        readOnly = !branch.headId.equals(id),
      )
  }
  def summarize(implicit session: DBSession): serialized.WorkflowSummary = 
  {
    makeSummary(Branch.get(branchId), actionModuleId.map { Module.get(_) })
  }

  def makeSummary(branch: Branch, actionModule: Option[Module]): serialized.WorkflowSummary =
    serialized.WorkflowSummary(
      id           = id,
      createdAt    = created,
      action       = action.toString,
      actionModule = actionModule.map { _.id },
      packageId    = actionModule.map { _.packageId },
      commandId    = actionModule.map { _.commandId },
    )

  def deleteWorkflow(implicit session: DBSession): Unit =
  {

    val (resultIds, moduleIds) = 
      withSQL {
        val c = Cell.syntax
        val m = Module.syntax
        select(c.resultId, m.id)
          .from(Cell as c)
          .join(Module as m)
          .where.eq(c.workflowId, id)
            .and.eq(c.moduleId, m.id)
      }.map { rs => (rs.longOpt(1), rs.long(2)) }
       .list.apply()
       .unzip

    // Delete Cells in the workflow
    withSQL { 
      val c = Cell.syntax
      deleteFrom(Cell as c)
        .where.eq(c.workflowId, id)
    }.update.apply()


    // Garbage Collect Results
    val resultsLosingAReference: Set[Identifier] = 
      resultIds.flatten.toSet
    val stillReferencedResults: Set[Identifier] =
      withSQL {
        val c = Cell.syntax
        select(c.resultId)
          .from(Cell as c)
          .where.in(c.resultId, resultsLosingAReference.toSeq)
      }.map { _.longOpt(1) }
       .list.apply()
       .flatten
       .toSet
    val resultsToTrash:Set[Identifier] = 
      resultsLosingAReference -- stillReferencedResults

    withSQL {
      val r = OutputArtifactRef.syntax
      deleteFrom(OutputArtifactRef)
        .where.in(r.resultId, resultsToTrash.toSeq)
    }.update.apply()

    withSQL {
      val r = InputArtifactRef.syntax
      deleteFrom(InputArtifactRef)
        .where.in(r.resultId, resultsToTrash.toSeq)
    }.update.apply()

    withSQL {
      val m = Message.syntax
      deleteFrom(Message)
        .where.in(m.resultId, resultsToTrash.toSeq)
    }.update.apply()

    withSQL {
      val r = Result.syntax
      deleteFrom(Result)
        .where.in(r.id, resultsToTrash.toSeq)
    }.update.apply()

    val modulesLosingAReference: Set[Identifier] =
      moduleIds.toSet
    val stillReferencedModules: Set[Identifier] =
      withSQL {
        val c = Cell.syntax
        select(c.moduleId)
          .from(Cell as c)
          .where.in(c.moduleId, resultsLosingAReference.toSeq)
      }.map { _.long(1) }
       .list.apply()
       .toSet
    val modulesToTrash: Set[Identifier] =
      modulesLosingAReference -- stillReferencedModules

    withSQL {
      val m = Module.syntax
      deleteFrom(Module)
        .where.in(m.id, modulesToTrash.toSeq)
    }.update.apply()
  }

  /**
   * Discard all results for the workflow, preparing it for re-execution
   * This shouldn't really be used in general, but is currently
   * needed as a work-around for importing exported files from classic
   * 
   * Remember that [[Workflow]] *can not* schedule its own execution (since 
   * the DBSession needs to be committed first).  The caller is responsible 
   * for invoking [[Scheduler]].
   */
  def discardResults()(implicit session: DBSession) =
  {
    withSQL { 
      val c = Cell.column
      update(Cell)
        .set(c.state -> ExecutionState.STALE, c.resultId -> None)
        .where.eq(c.workflowId, id)
    }.update.apply()
  }

  def actionModule(implicit session: DBSession): Option[Module] =
    actionModuleId.map { Module.get(_) }
}
object Workflow 
  extends SQLSyntaxSupport[Workflow]
{
  def apply(rs: WrappedResultSet): Workflow = autoConstruct(rs, (Workflow.syntax).resultName)
  override def columns = Schema.columns(table)

  def getLength(workflowId: Identifier)(implicit session:DBSession) =
    sql"select max(position) from cell where workflow_id = $workflowId"
      .map { _.intOpt(1).map { _ + 1 } }.single.apply().flatten.getOrElse { 0 }

  def get(target: Identifier)(implicit session:DBSession): Workflow = getOption(target).get
  def getOption(target: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.id, target)  
    }.map { apply(_) }.single.apply()

  def getOption(branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.id, workflowId)  
          .and.eq(w.branchId, branchId)  
    }.map { apply(_) }.single.apply()

  def getOption(projectId: Identifier, branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      val b = Branch.syntax 
      select
        .from(Workflow as w)
        .join(Branch as b)
        .where.eq(w.id, workflowId)  
          .and.eq(w.branchId, branchId)  
          .and.eq(b.id, w.branchId)  
          .and.eq(b.projectId, projectId)
    }.map { apply(_) }.single.apply()


  def abortIfNeeded(branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Unit =
  {
    val pendingCellCount = withSQL {
      val c = Cell.syntax
      select(sqls"count(*)")
        .from(Cell as c)
        .where.in(c.state, ExecutionState.PENDING_STATES.toSeq)
          .and.eq(c.workflowId, workflowId)
    }.map { _.long(1) }.single.apply().getOrElse { 0l }
    if(pendingCellCount > 0){ abort(branchId, workflowId) }
  }
  def abort(branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Unit =
  {
    withSQL {
      val w = Workflow.column
      update(Workflow)
        .set(w.aborted -> 1)
        .where.eq(w.id, workflowId)
    }.update.apply()

    val affectedCells = withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, workflowId)
          .and.in(sqls"state", ExecutionState.PENDING_STATES.toSeq)
    }.map { Cell(_) }.list.apply()


    for(cell <- affectedCells) {
      DeltaBus.notifyStateChange(
        branchId = branchId, 
        cell.position, 
        ExecutionState.CANCELLED,
        cell.timestamps
      )
    }
    val stateTransitions = 
      StateTransition.forAll( 
        ExecutionState.PENDING_STATES -> ExecutionState.CANCELLED 
      )
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(
          c.state -> StateTransition.updateState(stateTransitions),
          c.resultId -> StateTransition.updateResult(stateTransitions)
        )
        .where.in(c.state, ExecutionState.PENDING_STATES.toSeq.map { _.id })
          .and.eq(c.workflowId, workflowId)
    }.update.apply()
  }

}

