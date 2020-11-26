package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import java.time.format.DateTimeFormatter
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI

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
{
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
  def abort(implicit session:DBSession): Workflow =
  {
    withSQL {
      val w = Workflow.column
      update(Workflow)
        .set(w.aborted -> 1)
        .where.eq(w.id, id)
    }.update.apply()
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(c.state -> ExecutionState.CANCELLED)
        .where.ne(c.state, ExecutionState.DONE)
          .and.eq(c.workflowId, id)
    }.update.apply()
    copy(aborted = true)
  }

  def outputArtifacts(implicit session: DBSession): Seq[ArtifactRef] =
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    withSQL {
      select(o.resultAll)
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .where.eq(c.resultId, o.resultId)
          .and.eq(c.workflowId, id)
        .orderBy(c.position.desc)
    }.map { OutputArtifactRef(_) }
     .list.apply()
     .foldLeft(Map[String, ArtifactRef]()) { 
      (scope:Map[String, ArtifactRef], artifact) =>
        // Thanks to the orderBy above, the first version of each identifier
        // that we encounter should be the right one.
        if(scope contains artifact.userFacingName) { scope }
        else { scope ++ Map(artifact.userFacingName -> artifact) }
     }
     .values.toSeq
  }

  def describe(implicit session: DBSession): JsObject = 
  {
    val branch = Branch.get(branchId)
    val cellsAndModules = cellsAndModulesInOrder
    val artifacts = outputArtifacts.filter { !_.artifactId.equals(None) }
                                   .map { ref => 
                                      ref.userFacingName -> 
                                        Artifact.lookupSummary(ref.artifactId.get).get 
                                    }
    val (datasets, dataobjects) =
      artifacts.partition { _._2.t.equals(ArtifactType.DATASET) }
    val summary = makeSummary(branch, actionModuleId.map { Module.get(_) })

    val isRunning = cellsAndModules.exists { !_._1.state.equals(ExecutionState.DONE) }

    val state = cellsAndModules.foldLeft(ExecutionState.DONE) { (prev, curr) =>
      if(!prev.equals(ExecutionState.DONE)){ prev }
      else { curr._1.state }
    }

    JsObject(
      summary.value ++ Map(
        HATEOAS.LINKS -> HATEOAS.extend(summary.value(HATEOAS.LINKS),
          HATEOAS.WORKFLOW_CANCEL -> (
            if(isRunning) { null }
            else { VizierAPI.urls.cancelWorkflow(branch.projectId, branchId, id) } 
          )
        ),
        "state" -> JsNumber(ExecutionState.translateToClassicVizier(state)),
        "modules" -> 
          Module.describeAll(
            projectId = branch.projectId,
            branchId = branchId,
            workflowId = id,
            cells = cellsAndModules
          ),
        "datasets" -> JsArray(datasets.map { case (name, d) => d.summarize(name) }),
        "dataobjects" -> JsArray(dataobjects.map { case (name, d) => d.summarize(name) }),
        "readOnly" -> JsBoolean(!branch.headId.equals(id))
      )
    )
  }
  def summarize(implicit session: DBSession): JsObject = 
  {
    makeSummary(Branch.get(branchId), actionModuleId.map { Module.get(_) })
  }

  def makeSummary(branch: Branch, actionModule: Option[Module]): JsObject =
    Json.obj(
      "id"          -> id.toString,
      "createdAt"   -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(created),
      "action"      -> action.toString,
      "packageId"   -> actionModule.map { _.packageId },
      "commandId"   -> actionModule.map { _.commandId },
      HATEOAS.LINKS -> HATEOAS(
        HATEOAS.SELF             -> VizierAPI.urls.getWorkflow(branch.projectId, branchId, id),
        HATEOAS.WORKFLOW_APPEND  -> VizierAPI.urls.appendWorkflow(branch.projectId, branchId, id),
        HATEOAS.WORKFLOW_BRANCH  -> VizierAPI.urls.getBranch(branch.projectId, branchId),
        HATEOAS.BRANCH_HEAD      -> VizierAPI.urls.getBranchHead(branch.projectId, branchId),
        HATEOAS.WORKFLOW_PROJECT -> VizierAPI.urls.getProject(branch.projectId),
        HATEOAS.FILE_UPLOAD      -> VizierAPI.urls.uploadFile(branch.projectId),
      )
    )

  def deleteWorkflow(implicit session: DBSession)
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
}
object Workflow 
  extends SQLSyntaxSupport[Workflow]
{
  def apply(rs: WrappedResultSet): Workflow = autoConstruct(rs, (Workflow.syntax).resultName)
  override def columns = Schema.columns(table)

  def getLength(workflowId: Identifier)(implicit session:DBSession) =
    sql"select max(position) from cell where workflow_id = $workflowId"
      .map { _.intOpt(1).map { _ + 1 } }.single.apply().flatten.getOrElse { 0 }

  def get(target: Identifier)(implicit session:DBSession): Workflow = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.id, target)  
    }.map { apply(_) }.single.apply()

  def lookup(branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.id, workflowId)  
          .and.eq(w.branchId, branchId)  
    }.map { apply(_) }.single.apply()

  def lookup(projectId: Identifier, branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): Option[Workflow] = 
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

}