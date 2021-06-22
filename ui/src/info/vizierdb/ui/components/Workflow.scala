package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.BranchSubscription
import rx._
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.RxBufferBase
import info.vizierdb.ui.rxExtras.RxBufferWatcher
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.types.ArtifactType

class Workflow(subscription: BranchSubscription)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews)

  val moduleNodes =
    RxBufferView(ul(), 
      moduleViewsWithEdits.rxMap { 
        case Left(module) => module.root
        case Right(edit) => edit.root
      }
    )


  val root = 
    div(id := "workflow",
      moduleNodes.root,
      div(
        button(
          onclick := { (e: dom.MouseEvent) => moduleViewsWithEdits.appendTentative() }, 
          "Add A Cell"
        )
      )
    )
}

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
  val edits: Iterable[TentativeModule] = elements.collect { case Right(x) => x }

  def refreshModuleState()
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

  def appendTentative() =
  {
    doAppend(Right(new TentativeModule(elements.size, this)))
    refreshModuleState()
  }

  def insertTentative(n: Int) =
  {
    doInsertAll(n, Some(Right(new TentativeModule(n, this))))
    refreshModuleState()
  }

  def dropTentative(m: TentativeModule) = 
  {
    onRemove(m.position)
    refreshModuleState()
  }

}
