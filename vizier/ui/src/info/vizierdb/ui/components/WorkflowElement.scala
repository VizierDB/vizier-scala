package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import info.vizierdb.serialized
import info.vizierdb.types._
import scala.annotation.tailrec
import info.vizierdb.util.LazyIterator
import info.vizierdb.ui.widgets.ScrollIntoView
import info.vizierdb.util.Logging


trait NoWorkflowOutputs
{
  val outputs: Rx[Map[String, Option[serialized.ArtifactSummary]]]
	   = Var[Map[String, Option[serialized.ArtifactSummary]]](Map.empty)
	val executionState = Var[ExecutionState.T](ExecutionState.DONE)
}


abstract class WorkflowElement(implicit owner: Ctx.Owner)
  extends Object
  with ScrollIntoView.CanScroll
  with Logging
{ 
  val root: dom.html.Element
  def id_attr: String
  val outputs: Rx[Map[String, Option[serialized.ArtifactSummary]]]
 	val executionState: Rx[ExecutionState.T]
 	val accumulatedExecutionState = Var[ExecutionState.T](ExecutionState.DONE)

  def tentativeModuleId: Option[Identifier]

  /**
   * The position of the element in the linked list
   */
  var displayPosition: Int = 0

  def isInjected = !this.isInstanceOf[Module]
  
  val visibleArtifacts = Var[Map[String, (serialized.ArtifactSummary, WorkflowElement)]](Map.empty)

  /**
   * Pointer to the preceding element (if one exists).  This is private so that ALL mutations
   * happen through the operators in this class
   */
  private var next: Option[WorkflowElement] = None
  /**
   * Pointer to the following element (if one exists).  This is private so that ALL mutations
   * happen through the operators in this class
   */
  private var prev: Option[WorkflowElement] = None

  def safeNext = next
  def safePrev = prev

  def propagateAllMyArtifacts(): Unit =
  {
    logger.trace(s"Propagating from $this: \n   Output: ${outputs.now}\n   Visible: ${visibleArtifacts.now}")
  	next.foreach { _.replaceArtifacts( visibleArtifactsAfterSelf ) }
  }

  def visibleArtifactsAfterSelf: Map[String, (serialized.ArtifactSummary, WorkflowElement)] =
  {
		val deleted = outputs.now.filter { _._2.isEmpty }.keySet
		val updated = outputs.now.collect { case (k, Some(v)) => k -> (v, this) }
		
		visibleArtifacts.now.filterNot { case (k, _) => deleted(k) } ++ updated
  }

  def replaceArtifacts(inputs: Map[String, (serialized.ArtifactSummary, WorkflowElement)]): Unit =
  {
    logger.trace(s"Updating artifacts at $this to : ${inputs.keys.mkString(", ")}")
  	visibleArtifacts() = inputs
    logger.trace(s"Visible now ${visibleArtifacts.now}")
    propagateAllMyArtifacts()
  }

  def propagateMyExecutionState()
  {
  	accumulatedExecutionState() = 
  		ExecutionState.merge(Seq(accumulatedExecutionState.now, executionState.now))
  }

  def initTriggers()
  {
    outputs.triggerLater { _ => propagateAllMyArtifacts() }
    executionState.triggerLater { _ => propagateMyExecutionState() }
  }

  @tailrec
  final def prevRealModule: Option[Module] = 
  	(this, prev) match {
  		case (m:Module, _) => Some(m)
  		case (_, None) => None
  		case (_, Some(p)) => p.prevRealModule
  	}
  def prevRealModuleExcludingSelf: Option[Module] =
  	prev.flatMap { _.prevRealModule }

  @tailrec
  final def nextRealModule: Option[Module] = 
  	(this, next) match {
  		case (m:Module, _) => Some(m)
  		case (_, None) => None
  		case (_, Some(n)) => n.nextRealModule
  	}
  def nextRealModuleExcludingSelf: Option[Module] =
  	next.flatMap { _.nextRealModule }

  def reverseIterator = new LazyIterator(Some(this), { e: WorkflowElement => e.prev })
  def iterator = new LazyIterator(Some(this), { e: WorkflowElement => e.next })

  def insertPosition: Int =
  	nextRealModule match {
  		case Some(m) => m.position.now
  		case None => 
  			prevRealModule match {
  				case Some(m) => m.position.now + 1
  				case None => 0
  			}
  	}

  def needsAppendToInsert = 
  	nextRealModule.isEmpty

  def isFirst = prev.isEmpty
  def isLast = next.isEmpty

  /**
   * Inserts the provided element after this element in the linked list.
   * 
   * Enforces the following invariants:
   * - The integrity of the linked list is preserved
   * - The visibleArtifacts fields of the entire linked list are maintained
   * - The displayPosition field of the entire linked list is maintained
   * - The accumulatedExecutionState field of the entire linked list is maintained
   */
  def linkElementAfterSelf(other: WorkflowElement)
  {
    assert(other.next.isEmpty && other.prev.isEmpty, "linkElementAfterSelf on node already in the list")
  	val nextNext = next
  	other.prev = Some(this)
  	next = Some(other)
  	other.next = nextNext
  	nextNext.foreach { _.prev = Some(other) }
  	propagateAllMyArtifacts()
  	propagateMyExecutionState()
    initTriggers()
  	var curr = next
  	var nextDisplayPosition = displayPosition+1
  	while(curr.isDefined){
  		curr.get.displayPosition = nextDisplayPosition
  		nextDisplayPosition += 1
  		curr = curr.get.next
  	}
  }

  /**
   * Inserts the provided element at the head of the linked list previously head.
   * 
   * Enforces the following invariants:
   * - The integrity of the linked list is preserved
   * - The visibleArtifacts fields of the entire linked list are maintained
   * - The displayPosition field of the entire linked list is maintained
   * - The accumulatedExecutionState field of the entire linked list is maintained
   */
  def linkSelfToHead(oldHead: WorkflowElement)
  {
    assert(next.isEmpty && prev.isEmpty, "linkSelfToHead on this already in the list")
    oldHead.prev = Some(this)
    next = Some(oldHead)
    propagateAllMyArtifacts()
    propagateMyExecutionState()
    initTriggers()
    var curr = next
    var nextDisplayPosition = displayPosition+1
    while(curr.isDefined){
      curr.get.displayPosition = nextDisplayPosition
      nextDisplayPosition += 1
      curr = curr.get.next
    }
  }

  /**
   * Replaces this element in the linked list with the provided element.
   * 
   * Enforces the following invariants:
   * - The integrity of the linked list is preserved
   * - The visibleArtifacts fields of the entire linked list are maintained
   * - The displayPosition field of the entire linked list is maintained
   * - The accumulatedExecutionState field of the entire linked list is maintained
   */
  def replaceSelfWithElement(other: WorkflowElement)
  {
  	other.next = next
  	other.prev = prev
    next.foreach { _.prev = Some(other) }
    prev.foreach { _.next = Some(other) }
  	this.next = None
  	this.prev = None
  	outputs.kill()
  	visibleArtifacts.kill()
  	other.prev.getOrElse{ other }.propagateAllMyArtifacts()
  	other.prev.getOrElse{ other }.propagateMyExecutionState()
    other.initTriggers()
  	other.displayPosition = displayPosition
  }

  /**
   * Removes this element from the linked list.
   * 
   * Enforces the following invariants:
   * - The integrity of the linked list is preserved
   * - The visibleArtifacts fields of the entire linked list are maintained
   * - The displayPosition field of the entire linked list is maintained
   * - The accumulatedExecutionState field of the entire linked list is maintained
   * 
   * returns the (prev, next) elements
   */
  def removeSelf(): (Option[WorkflowElement], Option[WorkflowElement]) =
  {
    logger.debug(s"Removing $this from list")
  	next.foreach { _.prev = this.prev }
  	prev.foreach { _.next = this.next }
  	outputs.kill()
  	visibleArtifacts.kill()
  	prev.foreach { _.propagateAllMyArtifacts() }
  	prev.foreach { _.propagateMyExecutionState() }
  	var curr = next
  	var nextDisplayPosition = displayPosition
  	while(curr.isDefined){
  		curr.get.displayPosition = nextDisplayPosition
  		nextDisplayPosition += 1
  		curr = curr.get.next
  	}
  	val ret = (prev, next)
  	this.next = None
  	this.prev = None
  	ret
  }

  /**
   * Validate the linked list
   */

  def validate(lastPosition: Int = -1)
  {
    next.foreach { n =>
      assert(n.prev.isDefined, s"${n}.prev is empty")
      assert(n.prev.get eq this, s"${n}.prev is not this ($this)")
      assert(n.displayPosition > displayPosition, s"${n} display position isn't increasing monotonically")
      val nextPosition = 
        this match {
          case m:Module => 
            assert(m.position.now > lastPosition, s"Modules out of order: $m with position ${m.position.now} comes after $lastPosition")
            m.position.now
          case _ => lastPosition
        }
      n.validate(nextPosition)
    }
  }

}