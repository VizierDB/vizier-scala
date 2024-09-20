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
package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.types.Identifier
import info.vizierdb.serialized
import info.vizierdb.util.Logging
import info.vizierdb.types._
import scala.collection.mutable.ArrayBuffer

/**
 * A wrapper around an [[RxBuffer]] of [[Module]] objects that allows "new"
 * modules (i.e., [[TentativeModule]] or [[ArtifactInspector]]) to be injected inline.  
 * 
 * Concretely, [[TentativeModule]] objects represent [[Module]] objects that have
 * not yet been allocated.  They typically do not have an identifier, and are not
 * available in the backend.  At some point, the [[TentativeModule]] will be 
 * allocated in the backend (and assigned an identifier), and then an insert or
 * append will allocate the module.
 * 
 * [[ArtifactInspector]] modules are "read only" modules that allow users to inspect
 * artifacts at that point in the workflow.
 * 
 * There are about four major "quirks" that this class needs to handle
 * 1. The list consists of [[Module]]s, [[TentativeModule]]s and [[ArtifactInspector]]s
 * 2. [[TentativeModule]]s and [[ArtifactInspector]]s do not count towards the positional 
 *    index provided by the source collection.  Translations must "skip" these.
 * 3. An `insert` `prepend`, or `append` may be used to replace a [[TentativeModule]] 
 *    with a corresponding [[Module]] once the module is allocated in the backend.
 * 4. This class is also responsible for maintaining visible Artifact maps for the
 *    provided modules.
 * 
 * As a note, this implementation currently has several linear-time operations, which
 * makes a lot of things quadratic.  In the interest of keeping the code simple for
 * the initial implementation, I'm keeping these as-is.  In the longer term (TODO) it
 * would probably be a good idea to implement this as a sort of Tree structure.  
 */
class TentativeEdits(val project: Project, val workflow: Workflow)
                    (implicit owner: Ctx.Owner)
  extends RxBuffer[WorkflowElement]
     with RxBufferWatcher[Module]
     with Logging
{

  def this(input: RxBuffer[Module], project: Project, workflow: Workflow)
          (implicit owner: Ctx.Owner)
  {
    this(project, workflow)
    input.iterator.foreach { this.onAppend(_) }
    input.deliverUpdatesTo(this)
  }

  object Tail extends WorkflowElement()(owner) with NoWorkflowOutputs
  {
    val id_attr = "workflow_tail"
    val root = div(id := id_attr).render
    def tentativeModuleId: Option[Identifier] = None
  }

  def state = Tail.accumulatedExecutionState
  def allArtifacts = Tail.visibleArtifacts

  override def apply(idx: Int): WorkflowElement = 
  {
    var ret: WorkflowElement = first;
    for(i <- 0 until idx) { ret = ret.safeNext.get }
    return ret
  }
  override def iterator = first.iterator.takeWhile { _ ne Tail }
  override def reverseIterator = Tail.reverseIterator.drop(1)
  override def length = Tail.displayPosition
  override def last = Tail.safePrev.get
  override def head = first

  var first: WorkflowElement = Tail
  val baseElements = ArrayBuffer[Module]()
  var tempAttrDomId = 0l

  def nextTempElementDomId: String =
  {
    val ret = s"element_${tempAttrDomId}"
    tempAttrDomId += 1
    return ret
  }

  private val watchers = ArrayBuffer[RxBufferWatcher[WorkflowElement]]()

  def deliverUpdatesTo[T2 <: RxBufferWatcher[WorkflowElement]](handler: T2): T2 =
  {
    watchers += handler
    RxBuffer.logger.trace(s"Registered watcher on $this (now ${watchers.size} watchers)")
    return handler
  }

  /**
   * Prepend the specified module to the list
   * 
   * This is implemented via insertAll to keep the code simple
   */
  override def onPrepend(sourceElem: Module): Unit = 
    onInsertOne(0, sourceElem)

  /**
   * Insert an item into the list
   */
  override def onInsertAll(n: Int, modules: Traversable[Module]): Unit = 
  {
    var idx = n
    for(m <- modules){
      onInsertOne(idx, m)
      idx += 1
    }
  }

  override def onClear()
  {
    while(!baseElements.isEmpty){ onRemove(baseElements.size-1) }
  }

  /**
   * Working backwards from [[node]] find a node with the target identifier  
   * 
   * This operation works backward from the specified node to find a [[TentativeModule]]
   * with the specified tentative identifier.  The search stops as soon as it hits
   * a "real" Module to avoid inserting into the wrong place in the list.
   */
  def findReplacementCandidate(targetId: Identifier, node: WorkflowElement): Option[WorkflowElement] =
  {
    logger.trace(
      s"Finding replacement starting from $node in ${node.reverseIterator.takeWhile { _.isInjected }.mkString("; ")}"
    )
    node.reverseIterator
        .takeWhile { _.isInjected }
        .find { curr => 
          logger.trace(s"Checking to see if $curr can replace $targetId")
          curr.tentativeModuleId.isDefined && curr.tentativeModuleId.get == targetId
        }
  }

  /**
   * Insert a single "real" module at the specified source position
   * 
   * This method works with [[WorkflowElement]] to manage the bookkeeping
   * of the list.  Most of the bookkeeping happens in 
   * [[WorkflowElement]]'s <tt>linkElementAfterSelf</tt> and <tt>replaceSelfWithElement</tt>
   * methods.  This method is mainly concerned with finding the correct point to
   * do the insertion and maintaining [[first]], [[last]], and [[baseElements]]
   * 
   * The other bit of magic performed by this method is management of interactions
   * between TentativeModule and the backend.  Under normal usage, we keep a TentativeModule
   * around as a placeholder while we wait for the backend to replicate the module back to 
   * us.  If a module is inserted with the same ID as a TentativeModule located at or around 
   * the same location (see `findReplacementCandidate`), the TentativeModule will be 
   * discarded (i.e., the backend has replicated the 'real' module back to us).
   */
  def onInsertOne(n: Int, module: Module): Unit =
  {
    logger.debug(s"INSERT @ $n / ${baseElements.size} of $module")
    assert(n <= baseElements.size)

    // Start by figuring out whether we're replacing a TentativeModule or if this insertion 
    // came from somewhere else (e.g., the spreadsheet button or another client connected to
    // the same workflow.
    // 
    // If the update was triggered by a TentativeModule located between the Nth and N+1th base 
    // modules, the replacement will arrive as an insertion at the N+1th base module position.
    // 
    // We use findReplacementCandidate to search the **tentative** list in the range between the
    // Nth and N+1th base modules (not inclusive).  Note that while this range will never contain
    // actual modules, it *may* contain tentative elements, including the one that we want to
    // replace.
    // 
    // findReplacementCandidate wants us to pass it the immediate predecessor of the N+1th base
    // module (predecessor, so that it can stop as soon as it hits the first module).
    // There are two additional corner cases that we need to handle:
    // - We're appending to the *end* of the list.  In this case, there is no N+1th module, so 
    //   we start the search from the list's tail.
    // - We're inserting at the 0th base module, and there are no tentative modules preceding it.
    //   In this case, just pass the 0th base module and findReplacementCandidate will return
    //   None immediately (and we fall through to normal replacement)
    val replacementSearchStart = 
      if(n == baseElements.size){ Tail }
      else { 
        // Note that 'safePrev' is just a read-only alias for 'prev'
        baseElements(n).safePrev.getOrElse { baseElements(n) } 
      }

    logger.debug(s"Starting replacement search at $replacementSearchStart")

    findReplacementCandidate(module.id, replacementSearchStart) match {

      // Possibility one: We found a TentativeModule with the same ID
      case Some(replacement) => 
        logger.debug(s"Insert will replace $replacement")
        if(replacement.isFirst){ first = module }
        replacement.replaceSelfWithElement(module)
        watchers.foreach { _.onUpdate(module.displayPosition, module) }

      // ... or there is no matching TentativeModule and this is just a straight insertion
      case None => {
        // ... so insert it after the first preceding real Module.
        // None => Insert at head, Some(x) => x is the preceding WorkflowElement
        val insertionPoint: Option[WorkflowElement] =
          // If no elements, insert at head
          if(baseElements.isEmpty) { None }
          // If appending, insert after tail
          else if(n >= baseElements.size){ Some(baseElements.last) }
          // Otherwise, insert it immediately after the N-1th base module (or head if N=0)
          // Sept 2024 by OK: I *think* this search is overkill... we could just use 
          //                  if(n > 0) { Some(baseElements(n-1)) } else { None }
          //                  but I don't want to poke the happy fun tentative view machine
          //                  any more than is strictly needed.
          else { baseElements(n).prevRealModuleExcludingSelf }

        logger.debug(s"Inserting at $insertionPoint")

        insertionPoint match {
          // Possibility two: Insert at the head
          case None => 
            logger.debug(s"Insert will be at head")
            module.linkSelfToHead(first)
            first = module
            watchers.foreach { _.onInsertAll(module.displayPosition, Seq(module)) }

          // Possibility three: Insert elsewhere
          case Some(prev) =>
            logger.debug(s"Insert will be after $prev")
            prev.linkElementAfterSelf(module)
            watchers.foreach { _.onInsertAll(module.displayPosition, Seq(module)) }

        }
      }
    }
    if(n == baseElements.size){ baseElements.append(module) }
    else { baseElements.insert(n, module) }
  }

  /**
   * Append an item into the list
   * 
   * Apart from basic list management, this function does one bit of bookkeeping:
   * 1. Replacing TentativeModules that have been updated
   * 
   * Updating the visibleArtifacts field is unnecessary as this is the last module
   */
  override def onAppend(sourceElem: Module): Unit = 
  {
    onInsertOne(baseElements.size, sourceElem)
  }

  /**
   * Remove an item from the list
   * 
   * Apart from basic list management, this function does three bits of bookkeeping:
   * 1. Translating the target position.
   * 2. Updating visibleArtifacts fields.
   */
  override def onRemove(n: Int): Unit =
  {
    logger.trace(s"ON REMOVE: $n")
    val module = baseElements.remove(n)
    logger.trace(s"  remove at position ${module.displayPosition}")
    watchers.foreach { _.onRemove(module.displayPosition) }
    val (oldPrev, oldNext) = module.removeSelf()
    if(oldPrev.isEmpty) { 
      assert(oldNext.isDefined)
      first = oldNext.get
    }
  }

  /**
   * Update an item in the list
   * 
   * Apart from basic list management, this function does three bits of bookkeeping:
   * 1. Translating the target position.
   * 2. Updating visibleArtifacts fields.
   */
  override def onUpdate(n: Int, replacement: Module): Unit =
  {
    logger.trace(s"ON UPDATE: $n; ")
    val module = baseElements(n)
    baseElements(n) = replacement
    if(module.isFirst){
      first = replacement
    }
    module.replaceSelfWithElement(replacement)
    logger.trace(s"... displayed at ${module.displayPosition}")
    watchers.foreach { _.onUpdate(module.displayPosition, replacement) }
  }

  def prependTentative(
    defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
  ): TentativeModule =
  {
    logger.trace(s"Prepending tentative module: Head = $head")
    val module = 
      new TentativeModule(this, nextTempElementDomId, defaultPackageList)
    module.linkSelfToHead(first)
    first = module
    watchers.foreach { _.onInsertAll(module.displayPosition, Seq(module)) }
    return module
  }

  /**
   * Insert a [[TentativeModule]] at the specified position (indexed with target indices)
   */
  def insertTentativeAfter(
    prev: WorkflowElement,
    defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
  ): TentativeModule =
  {
    val module = 
      new TentativeModule(this, nextTempElementDomId, defaultPackageList)
    prev.linkElementAfterSelf(module)
    watchers.foreach { _.onInsertAll(module.displayPosition, Seq(module)) }
    return module
  }

  /**
   * Insert a [[TentativeModule]] at the end of the workflow
   */
  def appendTentative(
    defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
  ): TentativeModule =
    Tail.safePrev match {
      case None => prependTentative(defaultPackageList)
      case Some(s) => insertTentativeAfter(s, defaultPackageList)
    }

  /**
   * Insert an [[ArtifactInspector]] at the specified position (indexed with target indices)
   */
  def insertInspectorAfter(
    prev: WorkflowElement,
  ): ArtifactInspector =
  {
    val module = 
      new ArtifactInspector(workflow, nextTempElementDomId)
    prev.linkElementAfterSelf(module)
    watchers.foreach { _.onInsertAll(module.displayPosition, Seq(module)) }
    return module
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropTentative(m: TentativeModule) = 
  {
    logger.trace(s"Drop ${m} @ ${m.displayPosition}")
    watchers.foreach { _.onRemove(m.displayPosition) }
    val (oldPrev, oldNext) = m.removeSelf()
    if(oldPrev.isEmpty) { 
      assert(oldNext.isDefined)
      first = oldNext.get
    }
    logger.trace(s"Propagate drop of ${m} @ ${m.displayPosition}; end = ${Tail.displayPosition}")
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropInspector(m: ArtifactInspector) = 
  {
    watchers.foreach { _.onRemove(m.displayPosition) }
    val (oldPrev, oldNext) = m.removeSelf()
    if(oldPrev.isEmpty) { 
      assert(oldNext.isDefined)
      first = oldNext.get
    }
  }

  /**
   * Cause all pending cells to be saved
   */
  def saveAllCells() =
  {
    first.iterator.collect {
      case m:Module => m.editor.now
      case m:TentativeModule => m.editor.now
    }.flatten.foreach { _.saveState() }
  }

}
