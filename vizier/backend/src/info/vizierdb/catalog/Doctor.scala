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

import scala.collection.mutable.Buffer
import scalikejdbc.DB
import info.vizierdb.types._

/**
 * Tools for sanity-checking a notebook, diagnosing potential reproducibility
 * flaws (and maybe eventually fixing them
 */
object Doctor
{
  /**
   * Run the full suite of tests
   */
  def checkup(): Seq[String] = 
  {
    CatalogDB.withDBReadOnly { Project.list(_) }
             .flatMap { checkProject(_) }
  }

  /**
   * Run tests for a single project
   */
  def checkProject(projectId: Identifier): Seq[String] =
    checkProject( CatalogDB.withDBReadOnly { Project.get(projectId)(_) } )

  /**
   * Run tests for a single project
   */
  def checkProject(project: Project): Seq[String] =
  {
    withErrors(s"In Project ${project.id}: ") { errors => 
      val branches =  CatalogDB.withDBReadOnly { project.branches(_) }
      val branchIds = branches.map { _.id }.toSet
      val activeBranchId = project.activeBranchId
      errors.assert(branchIds(activeBranchId)){
        s"Active branch $activeBranchId not listed as a project branch (${branchIds.mkString(", ")})"
      }
      for(branch <- branches){
        errors.sub(s"In Branch ${branch.id}: ") { errors => 
          errors.assert(branch.projectId == project.id){
            s"Branch belongs to a different project ${branch.projectId}"
          }
          errors.assert(branch.createdFromBranchId.map { branchIds(_) }.getOrElse(true)){
            s"Branch created from a branch in a different project ${branch.createdFromBranchId}"
          }
          val progenitorWorkflow = CatalogDB.withDBReadOnly { branch.createdFromWorkflow(_) }
          val workflows = CatalogDB.withDBReadOnly { branch.workflows(_) }
          val workflowIds = workflows.map { _.id }.toSet
          val workflowsWithProgenitorById = (workflows ++ progenitorWorkflow).map { x => x.id -> x }.toMap
          val headId = branch.headId
          errors.assert(workflowIds(headId))(
            s"Head workflow $headId not listed as a branch workflow (${workflowIds.mkString(", ")})"
          )
          var lastWorkflowCells = Seq[Cell]()
          for(workflow <- workflows){
            errors.sub(s"In Workflow ${workflow.id}: ") { errors => 
              errors.assert(workflow.branchId == branch.id) {
                s"Workflow belongs to a different branch (${workflow.branchId})"
              }
              val priorWorkflow = workflow.prevId.map { workflowsWithProgenitorById(_) }
              errors.assert(workflow.prevId.isEmpty || priorWorkflow.isDefined){
                s"Workflow progenitor ${workflow.prevId.getOrElse("x")} not a part of this branch or its source (${workflowIds.mkString(", ")}; ${branch.createdFromWorkflowId.getOrElse("-")})"
              }
              val priorWorkflowModules = CatalogDB.withDBReadOnly { implicit s => 
                                           priorWorkflow.map { _.modules } 
                                         }.getOrElse(Seq.empty)
              val cellsAndModules = CatalogDB.withDBReadOnly { workflow.cellsAndModulesInOrder(_) }
              val moduleIds = cellsAndModules.map { _._2.id }.toSet
              val modulePrecursors = cellsAndModules.flatMap { _._2.revisionOfId }.toSet
              workflow.actionModuleId.foreach { actionModuleId => 
                workflow.action match {
                  case ActionType.CREATE | ActionType.INSERT | ActionType.UPDATE | ActionType.APPEND => 
                    errors.assert(moduleIds(actionModuleId) || modulePrecursors(actionModuleId)){
                      s"Workflow action module ${CatalogDB.withDBReadOnly { workflow.actionModule(_) }} not in the current workflow (module ids: ${moduleIds.mkString(", ")})"
                    }
                  case ActionType.FREEZE  => ()
                  case ActionType.DELETE => 
                    errors.assert(priorWorkflowModules.map { _.id }.contains(actionModuleId)){
                      s"Workflow action module ${CatalogDB.withDBReadOnly { workflow.actionModule(_) }} not in the progenitor workflow ${priorWorkflow.map { _.id }.getOrElse("<none>")} (module ids: ${priorWorkflowModules.map { _.id }.mkString(", ")})"
                    }
                }
              }
              for( (cell, module) <- cellsAndModules ){
                module.revisionOfId.isEmpty
              }
            }
          }
        }
      }
    }
  }

  def withErrors(prefix: String)(exec: Errors => Unit): Seq[String] = 
  {
    val errors = new Errors(prefix)
    try { 
      exec(errors)
    } catch {
      case e: Throwable => 
        errors.add(Seq("Error during Doctor: "+e.getMessage()))
    }
    return errors.toSeq
  }

  class Errors(prefix: String)
  {
    val list = Buffer[String]()
    def toSeq: Seq[String] = list.toSeq
    def assert(condition: Boolean)(message: => String) =
      if(condition == false) { list.append(prefix + message) }
    def add(messages: Seq[String], prefix: String = "") =
      list.append(messages.map { prefix+_ }:_*)
    def sub(subPrefix: String)(exec: Errors => Unit): Unit =
    {
      val subErrors = withErrors(subPrefix)(exec)
      if(subErrors.size > 0){
        list.append(prefix)
        add(subErrors, "   ")
      }
    }
  }
}