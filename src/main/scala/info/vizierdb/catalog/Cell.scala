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
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import java.time.ZonedDateTime
import info.vizierdb.VizierException
import info.vizierdb.viztrails.{ 
  Provenance, 
  ProvenanceForStaleModule,
  ProvenanceForInvalidState,
  ValidProvenance
}

/**
 * One cell in a [[Workflow]], assigning a computation described by a [[Module]] to
 * a position in the workflow.
 * 
 * Broadly, a cell is a Many/Many relationship between [[Workflow]] and [[Module]]. 
 * Each cell is identified by its parent workflow and a unique, contiguous, 
 * zero-indexed position in that workflow.
 *
 * The cell is parameterized by a [[Module]] definition (referenced by moduleId), and  
 * may optionally point to a [[Result]] (referenced by resultId).
 *
 * Cells are immutable once created, with the exception of the resultId and state 
 * fields.  Both of these fields are intended to conform to monotonicity guarantees
 * detailed <a href="https://github.com/VizierDB/vizier-scala/wiki/DevGuide-CellStates">here</a>
 */
case class Cell(
  workflowId: Identifier,
  position: Int,
  moduleId: Identifier,
  resultId: Option[Identifier],
  state: ExecutionState.T,
  created: ZonedDateTime
)
{
  def module(implicit session: DBSession) = Module.get(moduleId)
  def workflow(implicit session: DBSession) = Workflow.get(workflowId)
  def result(implicit session: DBSession) = resultId.map { Result.get(_) }
  def moduleDescriptor: String =
    s"${moduleId}_${resultId.getOrElse {"noresult"}}"

  def inputs(implicit session: DBSession): Seq[ArtifactRef] = 
    withSQL { 
      val i = InputArtifactRef.syntax
      select.from(InputArtifactRef as i).where.eq(i.resultId, resultId)
    }.map { InputArtifactRef(_) }.list.apply()
  def outputs(implicit session: DBSession): Seq[ArtifactRef] = 
    withSQL { 
      val o = OutputArtifactRef.syntax
      select.from(OutputArtifactRef as o).where.eq(o.resultId, resultId)
    }.map { OutputArtifactRef(_) }.list.apply()
  def provenance(implicit session: DBSession): Provenance =
    if(resultId.isEmpty){
      ProvenanceForStaleModule(moduleId)
    } else if(ExecutionState.PROVENANCE_NOT_VALID_STATES(state)){
      ProvenanceForInvalidState(state)
    } else {
      ValidProvenance(
        inputs.map { ref => ref.userFacingName -> ref.artifactId.get }
              .toMap,
        outputs.map { ref => ref.userFacingName -> ref.artifactId }
               .toMap
      )
    }
  def messages(implicit session: DBSession):Iterable[Message] = 
    withSQL { 
      val m = Message.syntax
      selectFrom(Message as m).where.eq(m.resultId, resultId)
    }.map { Message(_) }.list.apply()
  def successors(implicit session: DBSession): Seq[Cell] = 
    withSQL { 
      val c = Cell.syntax
      select.from(Cell as c)
            .where.eq(c.workflowId, workflowId).and.gt(c.position, position)
            .orderBy(c.position.asc)
    }.map { Cell(_) }.list.apply()
  def successorsWithModules(implicit session: DBSession): Seq[(Cell, Module)] = 
    withSQL { 
      val c = Cell.syntax
      select.from(Cell as c)
            .where.eq(c.workflowId, workflowId).and.gt(c.position, position)
            .orderBy(c.position.asc)
    }.map { row => (Cell(row), Module(row)) }.list.apply()

  def projectId(implicit session: DBSession): Identifier = 
    withSQL {
      val w = Workflow.syntax
      val b = Branch.syntax
      select(b.projectId)
          .from(Workflow as w)
          .join(Branch as b)
          .where.eq(w.branchId, b.id)
            .and.eq(w.id, workflowId)
    }.map { _.get[Identifier](1)}.single.apply().get

  def start(implicit session: DBSession): (Cell, Result) = 
  {
    val newResultId = withSQL {
      val r = Result.column
      insertInto(Result).
        namedValues(r.started -> ZonedDateTime.now())
    }.updateAndReturnGeneratedKey.apply()
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(
          c.resultId -> newResultId, 
          c.state -> ExecutionState.RUNNING
        )
        .where.eq(c.workflowId, workflowId)
          .and.eq(c.position, position)
    }.update.apply()
    return (copy(
              resultId = Some(newResultId), 
              state = ExecutionState.RUNNING
            ), Result.get(newResultId))
  }
  def finish(state: ExecutionState.T)(implicit session: DBSession): (Cell, Result) = 
  {
    val resultId = this.resultId.getOrElse {
      throw new VizierException("Attempting to finish un-started cell $this")
    }
    val newCell = updateState(state)
    withSQL { 
      val r = Result.column
      update(Result)
        .set(r.finished -> ZonedDateTime.now())
        .where.eq(r.id, resultId)
    }.update.apply()

    return (newCell, Result.get(resultId))
  }
  def updateState(state: ExecutionState.T)(implicit session: DBSession): Cell =
  {
    withSQL { 
      val c = Cell.column
      update(Cell)
        .set(c.state -> state)
        .where.eq(c.workflowId, workflowId)
          .and.eq(c.position, position)
    }.update.apply()
    copy(state = state)
  }
  def replaceArguments(arguments: JsObject)(implicit session: DBSession): Cell =
  {
    val updatedModule = module.replaceArguments(arguments)
    withSQL { 
      val c = Cell.column
      update(Cell)
        .set(c.moduleId -> updatedModule.id)
        .where.eq(c.workflowId, workflowId)
          .and.eq(c.position, position)
    }.update.apply()
    copy(moduleId = updatedModule.id)
  }

  override def toString = s"Workflow $workflowId @ $position: Module $moduleId ($state)"
}
object Cell 
  extends SQLSyntaxSupport[Cell]
{
  def apply(rs: WrappedResultSet): Cell = autoConstruct(rs, (Cell.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(workflowId: Identifier, position: Int)(implicit session:DBSession): Cell = getOption(workflowId, position).get
  def getOption(workflowId: Identifier, position: Int)(implicit session:DBSession): Option[Cell] = 
    withSQL { 
      val c = Cell.syntax 
      select
        .from(Cell as c)
        .where.eq(c.workflowId, workflowId).and.eq(c.position, position)
    }.map { apply(_) }.single.apply()

  def abortEverything()(implicit session: DBSession) =
  {
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(c.state -> ExecutionState.ERROR)
        .where.ne(c.state, ExecutionState.ERROR)
          .and.ne(c.state, ExecutionState.DONE)
          .and.ne(c.state, ExecutionState.FROZEN)
    }.update.apply()
  }

  /** 
   * Manually create a cell (DO NOT USE)
   * 
   * Self-contained mechanism for creating cells.  In general, this method
   * should be avoided.  
   * 
   * USE CLASS METHODS ON [[Branch]] INSTEAD
   * 
   * This method is here mainly to facilitate the manual manipulation needed
   * for import/export.
   */
  private[vizierdb] def make(
    workflowId: Identifier,
    position: Int,
    moduleId: Identifier,
    resultId: Option[Identifier],
    state: ExecutionState.T,
    created: ZonedDateTime
  )(implicit session: DBSession): Identifier = 
      withSQL {
        val c = Cell.column
        insertInto(Cell)
          .namedValues(
            c.workflowId -> workflowId,
            c.position -> position,
            c.moduleId -> moduleId,
            c.resultId -> None,
            c.state -> ExecutionState.STALE,
            c.created -> ZonedDateTime.now()
          )
      }.updateAndReturnGeneratedKey.apply()
}

