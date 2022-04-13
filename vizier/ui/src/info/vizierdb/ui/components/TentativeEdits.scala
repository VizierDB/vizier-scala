package info.vizierdb.ui.components


import rx._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.types.Identifier
import info.vizierdb.serialized
import info.vizierdb.util.Logging

/**
 * A wrapper around an [[RxBuffer]] of [[Module]] objects that allows "new"
 * modules (i.e., [[TentativeModule]]) to be injected inline.  
 * 
 * Concretely, [[TentativeModule]] objects represent [[Module]] objects that have
 * not yet been allocated.  They typically do not have an identifier, and are not
 * available in the backend.  At some point, the [[TentativeModule]] will be 
 * allocated in the backend (and assigned an identifier), and then an insert or
 * append will allocate the module.
 * 
 * There are about four major "quirks" that this class needs to handle
 * 1. The list consists of both [[Module]]s and [[TentativeModule]]s
 * 2. [[TentativeModule]]s do not count towards the positional index provided
 *    by the source collection.  Translations must "skip" these.
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
  extends RxBufferBase[Module,Either[Module,TentativeModule]]
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

  val derive = Left(_)
  val allArtifacts = Var[Rx[Map[String, serialized.ArtifactSummary]]](Var(Map.empty))

  /**
   * Retrieve a list of all "tentative" modules currently being edited
   */
  def edits: Iterable[TentativeModule] = elements.collect { case Right(x) => x }

  /**
   * Update all module state for the workflow
   * 
   * Postconditions:
   * - All [[TentativeModule]]s' `visibleArtifacts` fields are valid
   * - All [[TentativeModule]]s' `position` fields are valid
   */
  private def refreshModuleState(from: Int = 0)
  {
    val visibleArtifacts:Rx[Map[String, serialized.ArtifactSummary]] = 
      if(from <= 0){
        Var(Map.empty)
      } else {
        elements(from-1) match {
          case Left(e) => e.visibleArtifacts.now
          case Right(e) => e.visibleArtifacts.now
        }
      }
    allArtifacts() =
      elements
        .zipWithIndex
        .drop(Math.max(from-1, 0))
        .foldLeft(visibleArtifacts) {
          case (artifacts, (Left(module), idx)) =>
            val outputs = module.outputs
            val oldArtifacts = artifacts


            val insertions: Rx[Map[String, serialized.ArtifactSummary]] = 
              outputs.map { _.filter { _._2.isDefined }.mapValues { _.get }.toMap }
            val deletions: Rx[Set[String]] = 
              outputs.map { _.filter { _._2.isEmpty }.keys.toSet }

            // println(s"Left starting with: ${artifacts.now.mkString(", ")} and adding ${insertions.now.mkString(", ")}")

            val updatedArtifacts = Rx { 
              val ret = (artifacts() -- deletions()) ++ insertions()
              ret
            }
            module.visibleArtifacts.now.kill()
            module.visibleArtifacts() = artifacts
            /* return */ updatedArtifacts
          case (artifacts, (Right(tentative), idx)) => 
            // println(s"Right sees: ${artifacts.now.mkString(", ")}")
            tentative.visibleArtifacts.now.kill()
            tentative.visibleArtifacts() = artifacts
            tentative.position = idx
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
      case ((sourceModules, idx), _) if sourceModules >= n => return idx
      case ((sourceModules, idx), Left(_))                 => (sourceModules+1, idx+1)
      case ((sourceModules, idx), Right(_))                => (sourceModules, idx+1)
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
    elements.drop(targetPosition)
            .takeWhile { _.isRight }
            .collect { case Right(t) => t }
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
            .takeWhile { _.isRight }
            .collect { case Right(t) => t }
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
          doUpdate(tentativeModule.position, Left(sourceElem))
        case None => {
          logger.trace(s"IN-PLACE: $sourceElem")
          doInsertAll(targetPosition, Seq(Left(sourceElem)))
          targetPosition = targetPosition + 
                              elements.drop(targetPosition)
                                      .takeWhile { _.isRight }
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
      case Some(tentativeModule) => doUpdate(tentativeModule.position, Left(sourceElem))
      case None => doAppend(Left(sourceElem))
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
    doAppend(Right(module))
    refreshModuleState()
    return module
  }

  /**
   * Insert a [[TentativeModule]] at the specified position
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
    doInsertAll(n, Some(Right(module)))
    refreshModuleState()
    return module
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropTentative(m: TentativeModule) = 
  {
    onRemove(m.position)
    refreshModuleState()
  }

  /**
   * Cause all pending cells to be saved
   */
  def saveAllCells() =
  {
    elements.map {
      case Left(module) => module.editor.now
      case Right(module) => module.editor.now
    }.foreach { _.foreach { _.saveState() } }
  }

}
