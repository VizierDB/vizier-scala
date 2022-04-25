package info.vizierdb.ui.components

import org.scalajs.dom
import rx._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.types.Identifier
import info.vizierdb.serialized
import info.vizierdb.util.Logging
import info.vizierdb.types._


sealed trait WorkflowElement
{ 
  def isInjected = true 
  def visibleArtifacts: Rx[Map[String, (serialized.ArtifactSummary, Module)]]
  def root: dom.Node
}
case class WorkflowModule(module: Module) extends WorkflowElement
{
  def visibleArtifacts = module.visibleArtifacts.now
  override def isInjected = false
  def root = module.root
}
case class WorkflowTentativeModule(module: TentativeModule) extends WorkflowElement
{
  def visibleArtifacts = module.visibleArtifacts.now
  def root = module.root
}
case class WorkflowArtifactInspector(inspector: ArtifactInspector) extends WorkflowElement
{
  def visibleArtifacts = inspector.visibleArtifacts.now
  def root = inspector.root
}

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
  extends RxBufferBase[Module,WorkflowElement]
     with RxBufferWatcher[Module]
     with Logging
{

  def this(input: RxBuffer[Module], project: Project, workflow: Workflow)
          (implicit owner: Ctx.Owner)
  {
    this(project, workflow)
    this.onInsertAll(0, input.elements)
    input.deliverUpdatesTo(this)
    refreshModuleState()
  }

  val derive = WorkflowModule(_)
  val allArtifacts = Var[Rx[Map[String, (serialized.ArtifactSummary, Module)]]](Var(Map.empty))
  val allStates = Var[Rx[ExecutionState.T]](Var(ExecutionState.DONE))
  val state = allStates.flatMap { x => x }

  /**
   * Retrieve a list of all "tentative" modules currently being edited
   */
  def edits: Iterable[TentativeModule] = elements.collect { case WorkflowTentativeModule(x) => x }

  /**
   * Update all module state for the workflow
   * 
   * Postconditions:
   * - All [[TentativeModule]]s' `visibleArtifacts` fields are valid
   * - All [[TentativeModule]]s' `position` fields are valid
   */
  private def refreshModuleState(from: Int = 0)
  {
    val visibleArtifacts:Rx[Map[String, (serialized.ArtifactSummary, Module)]] = 
      if(from <= 0){
        Var(Map.empty)
      } else {
        elements(from-1).visibleArtifacts
      }
    allStates() = 
      Rx {
        ExecutionState.merge( elements.collect { 
          case WorkflowModule(module) => module.subscription.state()
        })
      } 

    allArtifacts() =
      elements
        .zipWithIndex
        .drop(Math.max(from-1, 0))
        .foldLeft(visibleArtifacts) {
          case (artifacts, (WorkflowModule(module), idx)) =>
            val outputs = module.outputs
            val oldArtifacts = artifacts

            val insertions: Rx[Map[String, (serialized.ArtifactSummary, Module)]] = 
              outputs.map { _.filter { _._2.isDefined }.mapValues { x => (x.get, module) }.toMap }
            val deletions: Rx[Set[String]] = 
              outputs.map { _.filter { _._2.isEmpty }.keys.toSet }

            // println(s"WorkflowModule starting with: ${artifacts.now.mkString(", ")} and adding ${insertions.now.mkString(", ")}")

            val updatedArtifacts = Rx { 
              val ret = (artifacts() -- deletions()) ++ insertions()
              ret
            }
            module.visibleArtifacts.now.kill()
            module.visibleArtifacts() = artifacts
            /* return */ updatedArtifacts
          case (artifacts, (WorkflowTentativeModule(tentative), idx)) => 
            // println(s"WorkflowTentativeModule sees: ${artifacts.now.mkString(", ")}")
            tentative.visibleArtifacts.now.kill()
            tentative.visibleArtifacts() = artifacts
            tentative.position = idx
            /* return */ artifacts
          case (artifacts, (WorkflowArtifactInspector(inspector), idx)) =>
            inspector.visibleArtifacts.now.kill()
            inspector.visibleArtifacts() = artifacts
            inspector.position = idx
            /* return */ artifacts
        }
  }

  /**
   * Convert a position identifier in the original list to the corresponding 
   * position in this list.  This will be the first position with n [[Module]]
   * objects to the left of it
   */
  def sourceToTargetPosition(n: Int): Int =
    elements.foldLeft( (0, 0) ) {
      case ((sourceModules, idx), _) if sourceModules > n                  => return idx
      case ((sourceModules, idx), WorkflowModule(_)) if sourceModules == n => return idx
      case ((sourceModules, idx), WorkflowModule(_))                       => (sourceModules+1, idx+1)
      case ((sourceModules, idx), WorkflowTentativeModule(_))              => (sourceModules, idx+1)
      case ((sourceModules, idx), WorkflowArtifactInspector(_))            => (sourceModules, idx+1)
    }._2

  /**
   * Find a candidate position for replacement.
   * 
   * Any [[TentativeModule]] in a contiguous run starting at the specified position 
   * is a potential candidate for replacement.  A potential candidate is to be
   * replaced if an id has been assigned to it, and it is the same as the id of the
   * inserted module.
   */
  def findInsertCandidate(targetPosition: Int, id: Identifier): Option[TentativeModule] = 
  {
    logger.trace(s"FIND INSERT: $id @ $targetPosition")
    elements.take(targetPosition)
            .reverse
            .takeWhile { _.isInjected }
            .collect { case WorkflowTentativeModule(t) => t }
            .find { t => 
              logger.trace(s"CHECK: ${t.id}")
              t.id.isDefined && t.id.get.equals(id) 
            }
  }

  /**
   * Find a candidate position for append.
   * 
   * Any [[TentativeModule]] in a contiguous run from the end is a potential candidate 
   * for replacement.  A potential candidate is to be replaced if an id has been 
   * assigned to it, and it is the same as the id of the appended module.
   */
  def findAppendCandidate(id: Identifier): Option[TentativeModule] = 
    elements.reverse
            .takeWhile { _.isInjected }
            .collect { case WorkflowTentativeModule(t) => t }
            .find { t => t.id.isDefined && t.id.get.equals(id) }


  /**
   * Prepend the specified module to the list
   * 
   * This is implemented via insertAll to keep the code simple
   */
  override def onPrepend(sourceElem: Module): Unit = 
    onInsertAll(0, Seq(sourceElem))

  /**
   * Insert an item into the list
   * 
   * Apart from basic list management, this function does three bits of bookkeeping:
   * 1. Translating the target position.
   * 2. Replacing TentativeModules that have been updated
   * 3. Updating visibleArtifacts fields.
   */
  override def onInsertAll(n: Int, sourceElems: Traversable[Module]): Unit = 
  {
    var targetPosition = sourceToTargetPosition(n)
    logger.trace(s"ON INSERT ALL: $n")
    for(sourceElem <- sourceElems) {
      findInsertCandidate(targetPosition, sourceElem.id) match {
        case Some(tentativeModule) => 
          logger.trace(s"REPLACING: $tentativeModule")
          doUpdate(tentativeModule.position, WorkflowModule(sourceElem))
        case None => {
          logger.trace(s"IN-PLACE: $sourceElem")
          doInsertAll(targetPosition, Seq(WorkflowModule(sourceElem)))
          targetPosition = targetPosition + 
                              elements.drop(targetPosition)
                                      .takeWhile { _.isInjected }
                                      .size + 1
        }
      }
    }
    refreshModuleState()
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
    logger.trace(s"ON APPEND: $sourceElem")
    findAppendCandidate(sourceElem.id) match {
      case Some(tentativeModule) => doUpdate(tentativeModule.position, WorkflowModule(sourceElem))
      case None => doAppend(WorkflowModule(sourceElem))
    }
    refreshModuleState(elements.size - 1)
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
    super.onRemove(sourceToTargetPosition(n))
    refreshModuleState()
  }

  /**
   * Update an item in the list
   * 
   * Apart from basic list management, this function does three bits of bookkeeping:
   * 1. Translating the target position.
   * 2. Updating visibleArtifacts fields.
   */
  override def onUpdate(n: Int, sourceElem: Module): Unit =
  {
    logger.trace(s"ON UPDATE: $n")
    super.onUpdate(sourceToTargetPosition(n), sourceElem)
    refreshModuleState()
  }

  /**
   * Append a [[TentativeModule]] to the end of the workflow
   */
  def appendTentative(
    defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
  ): TentativeModule =
  {
    val module = 
      new TentativeModule(
            position = elements.size, 
            editList = this, 
            defaultPackageList = defaultPackageList
          )
    doAppend(WorkflowTentativeModule(module))
    refreshModuleState()
    return module
  }

  /**
   * Insert a [[TentativeModule]] at the specified position (indexed with target indices)
   */
  def insertTentative(
    n: Int,
    defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
  ): TentativeModule =
  {
    val module = 
      new TentativeModule(
            position = n, 
            editList = this,
            defaultPackageList = defaultPackageList
          )
    doInsertAll(n, Some(WorkflowTentativeModule(module)))
    refreshModuleState()
    return module
  }

  /**
   * Insert an [[ArtifactInspector]] at the specified position (indexed with target indices)
   */
  def insertInspector(
    n: Int,
  ): ArtifactInspector =
  {
    val inspector = 
      new ArtifactInspector(
            position = n, 
            workflow,
            if(n < 1){ Var(Var(Map.empty)) }
            else { Var(elements(n-1).visibleArtifacts) },
          )
    doInsertAll(n, Some(WorkflowArtifactInspector(inspector)))
    refreshModuleState()
    return inspector
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropTentative(m: TentativeModule) = 
  {
    super.onRemove(m.position)
    refreshModuleState()
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropInspector(m: ArtifactInspector) = 
  {
    super.onRemove(m.position)
    refreshModuleState()
  }

  /**
   * Cause all pending cells to be saved
   */
  def saveAllCells() =
  {
    elements.flatMap {
      case WorkflowModule(module) => module.editor.now
      case WorkflowTentativeModule(module) => module.editor.now
      case WorkflowArtifactInspector(module) => None
    }.foreach { _.saveState() }
  }

}
