package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import java.time.ZonedDateTime

import info.vizierdb.catalog.binders._

/**
 * A vistrails project.  The project may have an optional set of user-defined properties.
 */
case class Project(
  id: Identifier, 
  name: String,
  activeBranchId: Identifier,
  properties: JsObject = Json.obj(),
  created: ZonedDateTime,
  modified: ZonedDateTime
)
{
  def branches(implicit session: DBSession) = 
    withSQL { 
      val b = Branch.syntax
      select.from(Branch as b).where.eq(b.projectId, id)
    }.map { Branch(_) }.list.apply()
  def activeBranch(implicit session: DBSession):Branch = 
    Branch.get(activeBranchId)

  def createBranch(
    name: String,
    properties: JsObject = Json.obj(), 
    activate: Boolean = false,
    isInitialBranch: Boolean = false
  )(implicit session: DBSession): (Project, Branch, Workflow) = {
    val b = Branch.column
    val now = ZonedDateTime.now()
    val branchId = withSQL {
      insertInto(Branch)
        .namedValues(
          b.projectId -> id, 
          b.name -> name, 
          b.properties -> properties, 
          b.headId -> 0, 
          b.created -> now, 
          b.modified -> now
        )
    }.updateAndReturnGeneratedKey.apply()

    var (branch, workflow) = {
      var branch = Branch.get(branchId)
      if(isInitialBranch){ branch.initWorkflow() }
      else { branch.cloneWorkflow(activeBranch.headId) }
    }

    val project = 
      if(activate){ activateBranch(branchId) }
      else { this }

    return (project, branch, workflow)
  }

  def activateBranch(branchId: Identifier)(implicit session: DBSession): Project = 
  {
    val now = ZonedDateTime.now()
    withSQL {
      val p = Project.column
      update(Project)
        .set(p.activeBranchId -> branchId,
             p.modified -> now)
        .where.eq(p.id, id)
    }.update.apply()
    this.copy(activeBranchId = branchId, modified = now)
  }
}
object Project
  extends SQLSyntaxSupport[Project]
{
  def apply(rs: WrappedResultSet): Project = autoConstruct(rs, (Project.syntax).resultName)
  override def columns = Schema.columns(table)
  def create(
    name: String, 
    properties: JsObject = Json.obj()
  )(implicit session:DBSession): Project = 
  {
    val project = get(
      withSQL {
        val p = Project.column
        val now = ZonedDateTime.now()
        insertInto(Project)
          .namedValues(
            p.name -> name, 
            p.activeBranchId -> 0, 
            p.properties -> properties, 
            p.created -> now, 
            p.modified -> now
          )
      }.updateAndReturnGeneratedKey.apply()
    )
    return project.createBranch("default", isInitialBranch = true, activate = true)._1
  }

  def get(target: Identifier)(implicit session:DBSession): Project = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Project] = 
    withSQL { 
      val p = Project.syntax 
      select
        .from(Project as p)
        .where.eq(p.id, target) 
    }.map { apply(_) }.single.apply()

  def activeBranchFor(id: Identifier)(implicit session:DBSession): Branch = 
    withSQL { 
      val p = Project.syntax
      val b = Branch.syntax
      select(b.resultAll)
        .from(Project as p)
        .join(Branch as b)
        .where.eq(p.activeBranchId, b.id)
          .and.eq(p.id, id)
    }.map { Branch(_) }.single.apply().get
  def activeHeadFor(id: Identifier)(implicit session:DBSession): Workflow = 
    withSQL { 
      val p = Project.syntax
      val b = Branch.syntax
      val w = Workflow.syntax
      select(w.resultAll)
        .from(Project as p)
        .join(Branch as b)
        .join(Workflow as w)
        .where.eq(p.activeBranchId, b.id)
          .and.eq(b.headId, w.id)
          .and.eq(p.id, id)
    }.map { Workflow(_) }.single.apply().get
}