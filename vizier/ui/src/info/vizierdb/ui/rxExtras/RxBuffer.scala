package info.vizierdb.ui.rxExtras

import rx.Var
import scala.collection.mutable
import info.vizierdb.util.Logging

abstract class RxBuffer[A]
  extends Seq[A]
{
  val elements = mutable.Buffer[A]()
  def apply(idx: Int) = elements(idx)
  def iterator = elements.iterator
  def length = { elements.length }
  lazy val rxLength = {
    val x = Var[Int](0)
    deliverUpdatesTo(new RxBufferTrigger[A] {
      def onBufferChange() = { 
        x() = length 
      }
      // Remove events trigger before the element is
      // deleted, so decrement preemptively
      override def onRemove(n: Int): Unit = {
        x() = length - 1 
      }
    })
    /* return */ x
  }

  def deliverUpdatesTo[T2 <: RxBufferWatcher[A]](handler: T2): T2
  def rxMap[B](f: A => B): DerivedRxBuffer[A, B] =
  { 
    val ret = new DerivedRxBuffer(f)
    ret.onInsertAll(0, elements)
    deliverUpdatesTo(ret)
    return ret
  }
}
object RxBuffer
  extends Object
  with Logging
{
  def apply[A](initial: A*): RxBufferVar[A] = 
    ofSeq(initial)

  def ofSeq[A](initial: Iterable[A]): RxBufferVar[A] = 
  {
    val ret = new RxBufferVar[A]()
    ret.appendAll(initial)
    return ret
  }

  var idCounter = 0
}

abstract class RxBufferBase[A,B]
  extends RxBuffer[B]
{
  var id = { RxBuffer.idCounter += 1; RxBuffer.idCounter }
  private val watchers = mutable.Buffer[RxBufferWatcher[B]]()

  val derive: A => B

  def onAppend(sourceElem: A): Unit =
  {
    RxBuffer.logger.trace(s"$this@$id += $sourceElem (${watchers.size} watchers)")
    doAppend(derive(sourceElem))
  }

  def doAppend(targetElem: B): Unit =
  {
    RxBuffer.logger.trace(s"Apply $this@$id += $targetElem (${watchers.size} watchers)")
    elements += targetElem
    watchers.foreach { x => RxBuffer.logger.trace(s"Notifying: $x"); x.onAppend(targetElem) }
  }

  def onPrepend(sourceElem: A): Unit =
  {
    RxBuffer.logger.trace(s"$sourceElem +=: $this@$id (${watchers.size} watchers)")
    doPrepend(derive(sourceElem))
  }

  def doPrepend(targetElem: B): Unit =
  {
    RxBuffer.logger.trace(s"Apply $targetElem +=: $this@$id (${watchers.size} watchers)")
    targetElem +=: elements
    watchers.foreach { _.onPrepend(targetElem) }
  }

  def onClear(): Unit = 
  {
    elements.clear()
    watchers.foreach { _.onClear() }
  }

  def onInsertAll(n: Int, sourceElems: collection.Traversable[A]) =
  {
    RxBuffer.logger.trace(s"$this@$id << $sourceElems (${watchers.size} watchers)")
    doInsertAll(n, sourceElems.map { derive(_) })
  }

  def doInsertAll(n: Int, targetElems: collection.Traversable[B]): Unit =
  {
    RxBuffer.logger.trace(s"Apply $this@$id << $targetElems (${watchers.size} watchers)")
    elements.insertAll(n, targetElems)
    watchers.foreach { _.onInsertAll(n, targetElems) }
  }

  def onRemove(n: Int): Unit =
  {
    watchers.foreach { _.onRemove(n) }
    elements.remove(n)
  }

  def onUpdate(n: Int, sourceElem: A): Unit =
  {
    doUpdate(n, derive(sourceElem))
  }

  def doUpdate(n: Int, targetElem: B): Unit =
  {
    elements.update(n, targetElem)
    watchers.foreach { _.onUpdate(n, targetElem) }
  }

  def deliverUpdatesTo[T2 <: RxBufferWatcher[B]](handler: T2): T2 =
  {
    watchers += handler
    RxBuffer.logger.trace(s"Registered watcher on $this@$id (now ${watchers.size} watchers)")
    return handler
  }
}

trait RxBufferWatcher[A]
{
  def onAppend(elem: A): Unit
  def onPrepend(elem: A): Unit
  def onClear(): Unit
  def onInsertAll(n: Int, elems: collection.Traversable[A]): Unit
  def onRemove(n: Int): Unit
  def onUpdate(n: Int, elem: A): Unit
}

trait RxBufferTrigger[A]
  extends RxBufferWatcher[A]
{
  def onBufferChange(): Unit

  def onAppend(elem: A): Unit = onBufferChange()
  def onPrepend(elem: A): Unit = onBufferChange()
  def onClear(): Unit = onBufferChange()
  def onInsertAll(n: Int, elems: collection.Traversable[A]): Unit = onBufferChange()
  def onRemove(n: Int): Unit = onBufferChange()
  def onUpdate(n: Int, elem: A): Unit = onBufferChange()
}

trait SimpleRxBufferWatcher[A]
  extends RxBufferWatcher[A]
{
  def onInsert(n: Int, elem: A)

  def onInsertAll(n: Int, elems: collection.Traversable[A]) =
    elems.toSeq
         .zipWithIndex
         .foreach { x => onInsert(n+x._2, x._1) }

}

class RxBufferVar[A]
  extends RxBufferBase[A,A]
  with mutable.Buffer[A]
{
  val derive = (a: A) => a

  def +=(elem: A) = 
    { onAppend(elem); this }
  
  def +=:(elem: A) = 
    { onPrepend(elem); this }

  def clear() =
    onClear()

  def insertAll(n: Int, elems: collection.Traversable[A]) =
    onInsertAll(n, elems)

  def remove(n: Int) = 
    { val v = apply(n); onRemove(n); v }

  def update(n: Int, elem: A) = 
    onUpdate(n, elem)
}

class DerivedRxBuffer[A,B](val derive: A => B)
  extends RxBufferBase[A,B]
  with RxBufferWatcher[A] 
  with Seq[B]
