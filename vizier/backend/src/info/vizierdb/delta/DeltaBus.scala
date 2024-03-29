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
package info.vizierdb.delta

import scalikejdbc.DBSession
import scala.collection.mutable
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.{ Workflow, Module, Cell, Artifact, ArtifactRef, Message }
import scalikejdbc.interpolation.SQLSyntax
import info.vizierdb.viztrails.ScopeSummary
import info.vizierdb.serialized.Timestamps
import info.vizierdb.catalog.Result
import play.api.libs.json.JsNumber

/**
 * A central hub for notifications about state changes on branches.  
 * 
 * Broadly, state changes (i.e., deltas) fall into two categories:
 * - Revisions: User-triggered modifications to the workflow that are 
 *              represented with a brand new workflow id.
 * - State Update: Small updates to the state of a workflow, typically as a
 *                 result of executing the workflow.  These do not change the
 *                 workflow itself, but *are* monotonic: For example, cell 
 *                 follows a state transition DAG (see [[Cell]]), and messages
 *                 and outputs are append-only.
 * 
 * In an ideal world, we'd have all of these notifications automagically 
 * generated by the SQL persistence layer.  For now, however, they need to be  
 * generated manually.  
 * 
 * Revisions all pass through either [[Branch]] (e.g., branch.append or 
 * branch.insert) or [[Workflow]] (e.g., workflow.abort).  The branch/workflow 
 * revision methods have been instrumented to deliver an appropriate delta 
 * notification.
 *
 * State updates are all triggered from [[Scheduler]], and likewise the 
 * corresponding methods have been instrumented to deliver an appropriate 
 * delta notification.
 */
object DeltaBus
  extends LazyLogging
{
  val subscriptions = mutable.Map[Identifier, mutable.Buffer[Subscription]]()

  /**
   * Register for delta notifications for a specific branch
   * @param branchId      The branch to receive notifications for
   * @param handler       The handler to run when a notification arrives
   * @param note          A short description of the subscription context for
   *                      debugging
   * @return              A [[Subscription]] object that can be used to manage
   *                      (e.g., cancel) the subscription. 
   */
  def subscribe(
    branchId: Identifier, 
    handler: (WorkflowDelta => Unit),
    note: String
  ): Subscription = 
  {
    val subscription = Subscription(branchId, handler, note)
    synchronized {
      subscriptions.getOrElseUpdate(branchId, mutable.Buffer.empty)
                   .append(subscription)
    }
    return subscription
  }

  /**
   * Cancel a delta notification subscription
   * @param subscription   The [[Subscription]] object returned by [[subscribe]]
   */
  def unsubscribe(subscription: Subscription) =
    synchronized { 
      subscriptions.get(subscription.branchId) match {
        case None => ()
        case Some(branchSubscriptions) => 
          val idx = branchSubscriptions.indexOf(subscription)
          if(idx >= 0){ branchSubscriptions.remove(idx) }
      }
    }

  /** 
   * Announce a delta notification to the bus
   * @param branchId       The [[Branch]] identifier.
   * @param delta          The [[WorkflowDelta]] to announce
   */
  def notify(branchId: Identifier, delta: WorkflowDelta) = 
  {
    val targets = synchronized { subscriptions.get(branchId)
                                              .map { _.toSeq }
                                              .getOrElse(Seq.empty) }
    targets.foreach { target => 
      try { target.handler(delta) }
      catch { 
        case e: Throwable => 
          logger.error(s"Error processing handler ${target.note}: $e")
      }
    }
  }

  /**
   * An opaque object recording a subscription
   */
  case class Subscription(
    branchId: Identifier, 
    handler: (WorkflowDelta => Unit),
    note: String
  )

  /**
   * Convenience method to announce cell update
   * 
   * @param cell         The newly inserted cell
   * @param module       The module referenced by the newly inserted cell
   * @param result       The result object referenced by the newly inserted cell (if it exists)
   * @param messages     The messages (if any) generaetd by the newly inserted cell
   * @param inputs       Identifiers of the artifacts read by this cell, along with their names
   * @param outputs      [[Artifact]]s written by this cell, along with their names
   * @param projectId    The id of the [[Project]] housing the branch housing the upddated workflow.
   * @param branchId     The id of the [[Branch]] housing the updated workflow
   * @param workflowId   The id of the <b>updated</b> [[Workflow]].
   * 
   * Nearly all of the above parameters can be inferred from the "cell" object, but doing so
   * requires multiple expensive round trips into the Catalog to retrieve values that the 
   * caller frequently already has access to.  Convenience methods exist on cell to retrieve
   * the values in question, so we're going to place an extra burden on the caller to explicitly
   * pay that cost if they feel it is necessary.
   */
  def notifyCellUpdates(
    cell: Cell, 
    module: Module,
    result: Option[Result], 
    messages: Seq[Message],
    inputs: Seq[(String, Identifier)],
    outputs: Seq[(String, Option[Artifact])],
    projectId: Identifier,
    branchId: Identifier,
    workflowId: Identifier
  )(implicit session: DBSession)
  {
    DeltaBus.notify(
      branchId,
      UpdateCell(
        module.describe(
          cell = cell,
          result = result,
          messages = messages,
          inputs = inputs,
          outputs = outputs,
          projectId = projectId,
          branchId = branchId,
          workflowId = workflowId,
        )(session)(),
        cell.position
      )
    )
  }

  /**
   * Convenience method to announce an updated set of arguments
   * @param  workflow  The [[Workflow]] at the head of the [[Branch]] 
   *                   <b>after</b> the update.
   * @param  cell      The [[Cell]] being updated.
   * @param  module    The [[Module]] with the updated arguments.
   */
  def notifyUpdateCellArguments(
    workflow: Workflow,
    cell: Cell,
    module: Module
  )(implicit session: DBSession)
  {
    DeltaBus.notify(
      workflow.branchId,
      UpdateCellArguments(
        cell.position,
        module.argumentList,
        module.id
      )
    )
  }

  /**
   * Convenience method to announce a set of cell inserts
   * @param cell         The newly inserted cell
   * @param module       The module referenced by the newly inserted cell
   * @param result       The result object referenced by the newly inserted cell (if it exists)
   * @param messages     The messages (if any) generaetd by the newly inserted cell
   * @param inputs       Identifiers of the artifacts read by this cell, along with their names
   * @param outputs      [[Artifact]]s written by this cell, along with their names
   * @param projectId    The id of the [[Project]] housing the branch housing the upddated workflow.
   * @param branchId     The id of the [[Branch]] housing the updated workflow
   * @param workflowId   The id of the <b>updated</b> [[Workflow]].
   * 
   * Nearly all of the above parameters can be inferred from the "cell" object, but doing so
   * requires multiple expensive round trips into the Catalog to retrieve values that the 
   * caller frequently already has access to.  Convenience methods exist on cell to retrieve
   * the values in question, so we're going to place an extra burden on the caller to explicitly
   * pay that cost if they feel it is necessary.
   * 
   */
  def notifyCellInserts(
    cell: Cell, 
    module: Module,
    result: Option[Result], 
    messages: Seq[Message],
    inputs: Seq[(String, Identifier)],
    outputs: Seq[(String, Option[Artifact])],
    projectId: Identifier,
    branchId: Identifier,
    workflowId: Identifier
  )(implicit session: DBSession)
  {
    DeltaBus.notify(
      branchId,
      InsertCell(
        module.describe(
          cell = cell,
          result = result,
          messages = messages,
          inputs = inputs,
          outputs = outputs,
          projectId = projectId,
          branchId = branchId,
          workflowId = workflowId,
        )(session)(),
        cell.position
      )
    )
  }

  /**
   * Convenience method to announce a set of cell inserts
   * @param cell         The newly inserted cell
   * @param module       The module referenced by the newly inserted cell
   * @param result       The result object referenced by the newly inserted cell (if it exists)
   * @param messages     The messages (if any) generaetd by the newly inserted cell
   * @param inputs       Identifiers of the artifacts read by this cell, along with their names
   * @param outputs      [[Artifact]]s written by this cell, along with their names
   * @param projectId    The id of the [[Project]] housing the branch housing the upddated workflow.
   * @param branchId     The id of the [[Branch]] housing the updated workflow
   * @param workflowId   The id of the <b>updated</b> [[Workflow]].
   */
  def notifyCellAppend(
    cell: Cell, 
    module: Module,
    result: Option[Result], 
    messages: Seq[Message],
    inputs: Seq[(String, Identifier)],
    outputs: Seq[(String, Option[Artifact])],
    projectId: Identifier,
    branchId: Identifier,
    workflowId: Identifier
  )(implicit session: DBSession)
  {
    DeltaBus.notify(
      branchId,
      InsertCell(
        module.describe(
          cell = cell,
          result = result,
          messages = messages,
          inputs = inputs,
          outputs = outputs,
          projectId = projectId,
          branchId = branchId,
          workflowId = workflowId,
        )(session)(),
        cell.position
      )
    )
  }

  /**
   * Convenience method to announce a cell deletion
   * @param workflow     The [[Workflow]] at the head of the [[Branch]]
   * @param position     The position of the deleted cell.
   */
  def notifyCellDelete(
    workflow: Workflow,
    position: Int,
  )
  {
    DeltaBus.notify(workflow.branchId, DeleteCell(position))
  }

  /**
   * Convenience method to announce a cell state change
   * @param workflow     The [[Workflow]] at the head of the [[Branch]]
   * @param position     The position of the cell being updated.
   * @param newState     The new state that the cell is transitioning to.
   */
  def notifyStateChange(
    workflow: Workflow,
    position: Int,
    newState: ExecutionState.T,
    newTimestamps: Timestamps,
  ):Unit = 
    notifyStateChange(
      branchId = workflow.branchId, 
      position = position,
      newState = newState,
      newTimestamps = newTimestamps
    )


  /**
   * Convenience method to announce a cell state change
   * @param workflow     The [[Workflow]] at the head of the [[Branch]]
   * @param position     The position of the cell being updated.
   * @param newState     The new state that the cell is transitioning to.
   */
  def notifyStateChange(
    branchId: Identifier,
    position: Int,
    newState: ExecutionState.T,
    newTimestamps: Timestamps,
  ): Unit =
  {
    DeltaBus.notify(branchId, UpdateCellState(position, newState, newTimestamps))
  }

  /**
   * Convenience method to announce a new message
   */
  def notifyMessage(
    workflow: Workflow,
    position: Int,
    stream: StreamType.T,
    mimeType: String,
    data: Array[Byte]
  )(implicit session: DBSession)
  {
    DeltaBus.notify(workflow.branchId, AppendCellMessage(
      position,
      stream,
      Message(
        resultId = -1l,
        mimeType = mimeType, 
        stream = stream, 
        data = data
      ).describe(session)()
    ))
  }

  /**
   * Convenience method to announce a new output artifact
   */
  def notifyUpdateDependencies(
    workflow: Workflow,
    position: Int,
    inputs: Map[String, Identifier],
    outputs: Seq[DeltaOutputArtifact]
  )(implicit session: DBSession)
  {
    DeltaBus.notify(workflow.branchId, UpdateCellDependencies(
      position,
      inputs.mapValues { JsNumber(_) },
      outputs
    ))
  }

  /**
   * Convenience method to announce a deleted output artifact
   */
  def notifyAdvanceResultId(
    workflow: Workflow,
    position: Int,
    resultId: Identifier
  )(implicit session: DBSession)
  {
    DeltaBus.notify(workflow.branchId, AdvanceResultId(
      position,
      resultId
    ))
  }

}