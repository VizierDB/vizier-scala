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
import java.time.ZonedDateTime

import java.time.format.DateTimeFormatter
import info.vizierdb.catalog.binders._
import info.vizierdb.shared.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.Vizier
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.serialized
import info.vizierdb.delta.{ DeltaBus, UpdateProjectProperties }

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

  def branchIds(implicit session: DBSession) = 
    withSQL { 
      val b = Branch.syntax
      select(b.id).from(Branch as b).where.eq(b.projectId, id)
    }.map { _.long(1) }.list.apply()

  def artifacts(implicit session: DBSession): Seq[Artifact] =
    withSQL {
      val a = Artifact.syntax
      select.from(Artifact as a).where.eq(a.projectId, id)
    }.map { Artifact(_) }.list.apply()

  def activeBranch(implicit session: DBSession):Branch = 
    Branch.get(activeBranchId)

  /**
   * Create a branch 
   *  
   * @param  name                        The initial name of the new branch
   * @param  properties                  The initial properties for the new branch
   * @param  activate                    Optional.  Pass true to make this the default 
   *                                     branch for the project.
   * @param  isInitialBranch             For internal use.  Pass true to make this the
   *                                     an initial "blank" workflow
   * @param  fromBranch                  Indicate the source branch (requires fromWorkflow set
   *                                     and isInitialBranch = false)
   * @param  fromWorkflow                Indicate the source workflow to clone (requires 
   *                                     fromBranch set and isInitialBranch = false)
   * @param  skipWorkflowInitialization  For internal use.  Pass true to make this branch
   *                                     set up with no workflow so that workflow 
   *                                     initialization can happens manually e.g., during 
   *                                     import. (forces activate = false, and results
   *                                     in the first / last return values being undefined)
   */
  def createBranch(
    name: String,
    properties: JsObject = Json.obj(), 
    activate: Boolean = false,
    isInitialBranch: Boolean = false,
    fromBranch: Option[Identifier] = None,
    fromWorkflow: Option[Identifier] = None,
    skipWorkflowInitialization: Boolean = false,
    createdAt: Option[ZonedDateTime] = None,
    modifiedAt: Option[ZonedDateTime] = None 
  )(implicit session: DBSession): (Project, Branch, Workflow) = {
    val b = Branch.column
    val now = ZonedDateTime.now()
    val sourceBranch = 
      if(isInitialBranch) { None }
      else { 
        Some(
          fromBranch.map { Branch.get(id, _) }
                    .getOrElse { activeBranch }
        )
      }
    val sourceWorkflowId =
      if(isInitialBranch) { None }
      else {
        Some( 
          fromWorkflow.map { Workflow.getOption(sourceBranch.get.id, _).get.id }
                      .getOrElse { sourceBranch.get.headId }
        )
      }

    val branchId = withSQL {
      insertInto(Branch)
        .namedValues(
          b.projectId -> id, 
          b.name -> name, 
          b.properties -> properties, 
          b.headId -> 0, 
          b.created -> createdAt.getOrElse { now }, 
          b.modified -> modifiedAt.orElse { createdAt }.getOrElse { now },
          b.createdFromBranchId -> sourceBranch.map { _.id },
          b.createdFromWorkflowId -> sourceWorkflowId
        )
    }.updateAndReturnGeneratedKey.apply()

    if(skipWorkflowInitialization){
      return (this, Branch.get(branchId), null)
    }
    var (branch, workflow) = {
      var branch = Branch.get(branchId)
      if(isInitialBranch){ branch.initWorkflow() }
      else { 
        branch.cloneWorkflow(sourceWorkflowId.get)
      }
    }

    val project = 
      if(activate){ activateBranch(branchId) }
      else { this }

    return (project, branch, workflow)
  }

  def updateProperties(
    name: String = null, 
    properties: Map[String,JsValue] = null
  )(implicit session: DBSession): Project =
  {
    val now = ZonedDateTime.now()
    withSQL {
      val p = Project.column
      scalikejdbc.update(Project)
        .set(p.name       -> Option(name).getOrElse { this.name },
             p.properties -> Option(properties)
                                  .map { JsObject(_) }
                                  .getOrElse { this.properties }
                                  .toString,
             p.modified   -> now)
        .where.eq(p.id, id)
    }.update.apply()
    for(branchId <- branchIds){
      DeltaBus.notify(branchId, UpdateProjectProperties(properties))
    }
    Project.get(id)
  }

  def activateBranch(branchId: Identifier)(implicit session: DBSession): Project = 
  {
    val now = ZonedDateTime.now()
    withSQL {
      val p = Project.column
      scalikejdbc.update(Project)
        .set(p.activeBranchId -> branchId,
             p.modified -> now)
        .where.eq(p.id, id)
    }.update.apply()
    this.copy(activeBranchId = branchId, modified = now)
  }

  def describe(implicit session:DBSession): serialized.ProjectDescription =
    summarize.toDescription(branches.map { _.summarize })

  def summarize: serialized.ProjectSummary = 
    serialized.ProjectSummary(
      id = id,
      createdAt = created,
      lastModifiedAt = modified,
      defaultBranch = activeBranchId,
      properties = serialized.PropertyList.toPropertyList(properties.value.toMap ++ Map("name" -> JsString(name))),
      links = HATEOAS(
        HATEOAS.SELF           -> VizierAPI.urls.getProject(id),
        HATEOAS.API_HOME       -> VizierAPI.urls.serviceDescriptor,
        HATEOAS.API_DOC        -> VizierAPI.urls.apiDoc,
        HATEOAS.PROJECT_DELETE -> VizierAPI.urls.deleteProject(id),
        HATEOAS.PROJECT_UPDATE -> VizierAPI.urls.updateProject(id),
        HATEOAS.BRANCH_CREATE  -> VizierAPI.urls.createBranch(id),
        HATEOAS.FILE_UPLOAD    -> VizierAPI.urls.uploadFile(id)
      ),
    )

  def deleteProject(implicit session: DBSession)
  {
    for(artifact <- artifacts){
      artifact.deleteArtifact
    }
    for(branch <- branches){
      branch.deleteBranch
    }
    withSQL {
      val p = Project.syntax
      deleteFrom(Project)
        .where.eq(p.id, id)
    }.update.apply()
  }
}
object Project
  extends SQLSyntaxSupport[Project]
{
  def apply(rs: WrappedResultSet): Project = autoConstruct(rs, (Project.syntax).resultName)
  override def columns = Schema.columns(table)
  def create(
    name: String, 
    properties: JsObject = Json.obj(),
    createdAt: Option[ZonedDateTime] = None,
    modifiedAt: Option[ZonedDateTime] = None,
    dontCreateDefaultBranch: Boolean = false
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
            p.created -> createdAt.getOrElse { now }, 
            p.modified -> modifiedAt.orElse { createdAt }.getOrElse { now }
          )
      }.updateAndReturnGeneratedKey.apply()
    )
    if(dontCreateDefaultBranch){
      return project
    } else {
      return project.createBranch("default", isInitialBranch = true, activate = true)._1
    }
  }

  def get(target: Identifier)(implicit session:DBSession): Project = getOption(target).get
  def getOption(target: Identifier)(implicit session:DBSession): Option[Project] = 
    withSQL { 
      val p = Project.syntax 
      select
        .from(Project as p)
        .where.eq(p.id, target) 
    }.map { apply(_) }.single.apply()
  def withName(name: String)(implicit session:DBSession): Option[Project] = 
    withSQL {
      val p = Project.syntax
      select
        .from(Project as p)
        .where.eq(p.name, name)
    }.map { apply(_) }.list.apply().headOption
  def list(implicit session:DBSession): Seq[Project] = 
    withSQL { 
      val p = Project.syntax 
      select
        .from(Project as p)
    }.map { apply(_) }.list.apply()

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

