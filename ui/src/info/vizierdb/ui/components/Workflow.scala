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
              (implicit owner: Ctx.Owner, data: Ctx.Data)
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
                    (implicit owner: Ctx.Owner, data: Ctx.Data)
  extends RxBufferBase[Module,Either[Module,TentativeModule]]
  with RxBufferWatcher[Module]
{

  def this(input: RxBuffer[Module])
          (implicit owner: Ctx.Owner, data: Ctx.Data)
  {
    this()
    this.onInsertAll(0, input.elements)
    input.deliverUpdatesTo(this)
  }

  val derive = Left(_)
  val edits: Iterable[TentativeModule] = elements.collect { case Right(x) => x }

  def refreshModuleState()
  {
    var artifacts: Rx[Map[String, ArtifactType.T]] = Var(Map.empty)
    elements.foreach { 
      case Left(module) => 
        artifacts = Rx { 
          val updates:Map[String,Option[ArtifactType.T]] = 
            module.outputs.apply().mapValues { _.t }
          (
            artifacts() 
              -- updates.filter { _._2.isEmpty }.keys
              ++ updates.filter { _._2.isDefined }.mapValues { _.get }
          )
        }
      case Right(tentative) => 
        artifacts.trigger { tentative.visibleArtifacts() = artifacts.now.toSeq }
    }
  }

  override def onPrepend(sourceElem: Module): Unit = 
  {
    for(edit <- edits) { edit.position += 1 }
    super.onPrepend(sourceElem)
  }

  override def onInsertAll(n: Int, sourceElems: Traversable[Module]): Unit = 
  {
    val elems = sourceElems.toSeq
    val size = elems.size
    for(edit <- edits) { if(edit.position <= n) { edit.position += size } }
    super.onInsertAll(n, elems)
  }

  override def onRemove(n: Int): Unit =
  {
    for(edit <- edits) { if(edit.position > n) { edit.position -= 1 } }
    super.onRemove(n)
  }

  def appendTentative() =
  {
    doAppend(Right(new TentativeModule(elements.size, this)))
  }

  def insertTentative(n: Int) =
  {
    doInsertAll(n, Some(Right(new TentativeModule(n, this))))
  }

  def dropTentative(m: TentativeModule) = 
  {
    onRemove(m.position)
  }

}
