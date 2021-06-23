package info.vizierdb.ui.components


import rx._
import info.vizierdb.ui.rxExtras._

/**
 * A wrapper around an [[RxBuffer]] of [[Module]] objects that allows "new"
 * modules (i.e., [[TentativeModule]]) to be injected inline.  
 */
class TentativeEdits()
                    (implicit owner: Ctx.Owner)
  extends RxBufferBase[Module,Either[Module,TentativeModule]]
  with RxBufferWatcher[Module]
{

  def this(input: RxBuffer[Module])
          (implicit owner: Ctx.Owner)
  {
    this()
    this.onInsertAll(0, input.elements)
    input.deliverUpdatesTo(this)
  }

  val derive = Left(_)
  
  /**
   * Retrieve a list of all "tentative" modules currently being edited
   */
  def edits: Iterable[TentativeModule] = elements.collect { case Right(x) => x }

  private def refreshModuleState()
  {
    elements
      .zipWithIndex
      .foldLeft(Var(Map.empty):Rx[Map[String, Artifact]]) {
        case (artifacts, (Left(module), idx)) =>
          val outputs = module.outputs
          outputs.trigger {
            // println(s"OUTPUTS CHANGED @ $idx: ${module.outputs}")
          }
          val oldArtifacts = artifacts

          val insertions: Rx[Map[String, Artifact]] = 
            outputs.map { _.filter { !_._2.isDeletion } }
          val deletions: Rx[Set[String]] = 
            outputs.map { _.filter { _._2.isDeletion }.keys.toSet }

          val updatedArtifacts = Rx { 
            val ret = (artifacts() -- deletions()) ++ insertions()
            // println(s"UPDATES @ $idx: $updates + $artifacts -> $ret")
            ret
          }
          // updatedArtifacts.trigger { println(s"ARTIFACTS CHANGED @ $idx: $artifacts")}
          // println(s"ARTIFACTS <- $module @ $idx: ${artifacts.now}; ${module.outputs}")
          /* return */ updatedArtifacts
        case (artifacts, (Right(tentative), idx)) => 
          // println(s"ARTIFACTS -> $tentative ($idx) <= $artifacts")
          tentative.visibleArtifacts.now.kill()
          tentative.visibleArtifacts() = artifacts
          tentative.position = idx
          /* return */ artifacts
      }
  }

  // Override changes to the workflow to recompute module states

  override def onPrepend(sourceElem: Module): Unit = 
  {
    super.onPrepend(sourceElem)
    refreshModuleState()
  }

  override def onInsertAll(n: Int, sourceElems: Traversable[Module]): Unit = 
  {
    super.onInsertAll(n, sourceElems)
    refreshModuleState()
  }

  override def onRemove(n: Int): Unit =
  {
    super.onRemove(n)
    refreshModuleState()
  }

  override def onUpdate(n: Int, sourceElem: Module): Unit =
  {
    super.onUpdate(n, sourceElem)
    refreshModuleState()
  }

  /**
   * Append a [[TentativeModule]] to the end of the workflow
   */
  def appendTentative() =
  {
    doAppend(Right(new TentativeModule(elements.size, this)))
    refreshModuleState()
  }

  /**
   * Insert a [[TentativeModule]] at the specified position
   */
  def insertTentative(n: Int) =
  {
    doInsertAll(n, Some(Right(new TentativeModule(n, this))))
    refreshModuleState()
  }

  /**
   * Drop the indicated [[TentativeModule]] from the workflow.
   */
  def dropTentative(m: TentativeModule) = 
  {
    onRemove(m.position)
    refreshModuleState()
  }

}
