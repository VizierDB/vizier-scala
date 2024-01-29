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
package info.vizierdb.viztrails

import play.api.libs.json.{ JsValue, JsObject }
import scalikejdbc._
import java.util.concurrent.{ ForkJoinTask, ForkJoinPool }
import com.typesafe.scalalogging.LazyLogging

import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.catalog.binders._
import info.vizierdb.catalog.{ Workflow, Cell, Result }
import info.vizierdb.delta.{ DeltaBus, DeltaOutputArtifact }
import info.vizierdb.util.UnsupportedFeature
import info.vizierdb.Vizier

object Scheduler
  extends LazyLogging
{
  lazy val workflowWorkers = new ForkJoinPool(Vizier.config.supervisorThreads())
  lazy val cellWorkers     = new ForkJoinPool(Vizier.config.workerThreads())
  val runningWorkflows = scala.collection.mutable.Map[Identifier,RunningWorkflow]()

  /**
   * Schedule a workflow for execution.  This should be automatically called from the branch
   * mutator operations.  <b>Do not call this from within a DB Session</b>
   */
  def schedule(workflow: Workflow) 
  {
    logger.debug(s"Scheduling Workflow ${workflow.id}")
    this.synchronized {
      logger.trace(s"Acquired scheduler lock for ${workflow.id}")
      if(runningWorkflows contains workflow.id){
        logger.warn(s"Ignoring attempt to reschedule workflow ${workflow.id}")
        return
      }
      logger.trace(s"Allocating execution manager for ${workflow.id}")
      val executor = new RunningWorkflow(
                      workflow, 
                      Vizier.mainClassLoader
                     )
      runningWorkflows.put(workflow.id, executor)
      logger.trace(s"Starting execution manager for ${workflow.id}")
      workflowWorkers.execute(executor)
      logger.trace(s"Done scheduling ${workflow.id}")
    }
  }

  /**
   * Abort a (possibly) runnign workflow workflow.  This shouldn't be called directly.  Instead
   * use Workflow.abort or one of Branch's mutator operations.  <b>Do not call this from within 
   * a DB Session</b>
   */
  def abort(workflowId: Identifier)
  {
    logger.debug(s"Trying to abort Workflow ${workflowId}")
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      logger.debug(s"Aborting Workflow ${workflowId}")
      if(!executor.isDone){ executor.abort() }
      runningWorkflows.remove(workflowId)
    }
  }

  /**
   * A list of currently running workflows
   */
  def running(implicit session: DBSession): Seq[Workflow] =
  {
    logger.debug("Getting running workflows")
    this.synchronized {
      runningWorkflows
        .filterNot { _._2.isDone }
        .map { _._1 }
        .toSeq
    }.map { 
      Workflow.get(_)
    }
  }


  /**
   * Free resources associated with the specified workflow if they are no longer needed.
   */
  def cleanup(workflowId: Identifier)
  {
    logger.debug("Cleaning up workflows")
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      if(executor.isDone) { runningWorkflows.remove(workflowId) }
    }
  }

  /**
   * Check to see if the specified workflow is still pending.  Aliased as Workflow.isPending
   */
  def isWorkflowPending(workflowId: Identifier): Boolean = 
  { 
    logger.debug("Checking for pending workflows")
    cleanup(workflowId); 
    this.synchronized { runningWorkflows contains workflowId } 
  }

  /**
   * Block until the specified workflow completes.  The workflow must already be scheduled.
   *
   * In general, this method should only be used for testing.  
   */
  def joinWorkflow(workflowId: Identifier, failIfNotRunning: Boolean = true)
  {
    logger.debug(s"Trying to join with Workflow ${workflowId}")
    val executorMaybe:Option[RunningWorkflow] = this.synchronized { 
      runningWorkflows.get(workflowId)
    }

    executorMaybe match {
      case None => 
        if(failIfNotRunning){
          throw new RuntimeException(s"Workflow $workflowId is not running or has already been cleaned up")
        }
      case Some(executor) =>
        logger.debug(s"Found a running workflow... blocking")
        executor.join() 
        logger.debug(s"Workflow complete.  Returned from block")
        cleanup(workflowId)
      }
  }

  /**
   * Block until all running workflows have quiesced
   */
  def joinAll()
  {
    var nextWorkflowId =
      this.synchronized { runningWorkflows.keys.headOption }
    while(!nextWorkflowId.isEmpty)
    {
      joinWorkflow(nextWorkflowId.get, failIfNotRunning = false)
      nextWorkflowId =
        this.synchronized { runningWorkflows.keys.headOption }
    }
  }
}

