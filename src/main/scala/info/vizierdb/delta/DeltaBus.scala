package info.vizierdb.delta

import scala.collection.mutable
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging

object DeltaBus
  extends LazyLogging
{
  val subscriptions = mutable.Map[Identifier, mutable.Buffer[Subscription]]()

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

  def unsubscribe(subscription: Subscription) =
    synchronized { 
      subscriptions.get(subscription.branchId) match {
        case None => ()
        case Some(branchSubscriptions) => 
          val idx = branchSubscriptions.indexOf(subscription)
          if(idx >= 0){ branchSubscriptions.remove(idx) }
      }
    }

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

  case class Subscription(
    branchId: Identifier, 
    handler: (WorkflowDelta => Unit),
    note: String
  )

}