package info.vizierdb.viztrails

import scalikejdbc._
import info.vizierdb.types.ExecutionState
import com.typesafe.scalalogging.LazyLogging

class StateTransition(
  fromState: ExecutionState.T,
  toState: ExecutionState.T,
  condition: Option[SQLSyntax] = None,
)
{
  def matcherSyntax = 
    condition match { 
      case None    => sqls"state = ${fromState.id}"
      case Some(c) => sqls"state = ${fromState.id} and $c"
    }

  def keepResult = 
    (fromState, toState) match {
      case (ExecutionState.RUNNING, ExecutionState.DONE) => true
      case (f, t) if ExecutionState.PROVENANCE_NOT_VALID_STATES(f)
                   && ExecutionState.PROVENANCE_VALID_STATES(t) => false
      case _ => true
    }

  def stateUpdateSyntax:SQLSyntax = 
    sqls"when $matcherSyntax then ${toState.id}"

  def resultUpdateSyntax:SQLSyntax = 
    sqls"when $matcherSyntax then ${if(keepResult){ sqls"result_id" } else { sqls"null" }}"

  override def toString(): String = 
    condition match {
      case None => s"$fromState -> $toState"
      case Some(c) => s"$fromState (if $c) -> $toState"
    }
}

/**
 * Utility methods for managing cell state transitions.
 * 
 * See https://github.com/VizierDB/vizier-scala/wiki/DevGuide-CellStates
 */
object StateTransition
  extends Object
  with LazyLogging
{
  /**
   * Declare a state transition: <pre>
   * StateTransition( RUNNING -> CANCELLED )
   * </pre>
   */
  def apply(fromTo: (ExecutionState.T, ExecutionState.T)): Seq[StateTransition] = 
    Seq(new StateTransition(
      fromState = fromTo._1, 
      toState = fromTo._2
    ))

  /**
   * Declare a state transition that applies only to specific cells: <pre>
   * StateTransition( sql"position > $position", RUNNING -> CANCELLED )
   * </pre>
   */
  def apply(condition: SQLSyntax, fromTo: (ExecutionState.T, ExecutionState.T)): Seq[StateTransition] = 
    Seq(new StateTransition(
      fromState = fromTo._1, 
      toState = fromTo._2, 
      condition = Some(condition)))

  /**
   * Declare state transitions that affect all cells with a given condition: <pre>
   * StateTransition( sql"position > $position", WAITING )
   * </pre>
   */
  def forAll(condition: SQLSyntax, to: ExecutionState.T): Seq[StateTransition] = 
    forAll(condition, ExecutionState.values -> to)

  /**
   * Declare state transitions that affect all cells with a state in a given list and a given 
   * condition: <pre>
   * StateTransition( sql"position > $position", ExecutionState.PENDING_STATES -> STALE )
   * </pre>
   */
  def forAll(condition: SQLSyntax, fromTo: (Iterable[ExecutionState.T], ExecutionState.T)): Seq[StateTransition] = 
    fromTo._1.map { state => 
      new StateTransition(
        fromState = state, 
        toState = fromTo._2, 
        condition = Some(condition)) 
    }.toSeq

  /**
   * Declare state transitions that affect all cells with a state in a given list: <pre>
   * StateTransition( ExecutionState.PENDING_STATES -> CANCELLED )
   * </pre>
   */
  def forAll(fromTo: (Iterable[ExecutionState.T], ExecutionState.T)): Seq[StateTransition] = 
    fromTo._1.map { state => 
      new StateTransition(
        fromState = state, 
        toState = fromTo._2) 
    }.toSeq

  /**
   * Generate a [[SQLSyntax]] case statement that derives the new cell state given a set
   * of declared state transitions.
   */
  def updateState(transitions: Seq[StateTransition]): SQLSyntax =
  {
    val result = 
      if(transitions.isEmpty){
        sqls"state"
      } else {
        transitions.map { _.stateUpdateSyntax }
                   .foldLeft(sqls"case") { _ + sqls" " + _ } + sqls" else state end"
      }
    logger.trace(s"State Transitions for: \n${transitions.mkString("\n")}\nare: ${result}")
    return result
  }

  /**
   * Generate a [[SQLSyntax]] case statement that derives the new result reference
   * of declared state transitions.
   * 
   * In particular, any state transition from a PROVENANCE_NOT_VALID state to a 
   * PROVENANCE_VALID state must invalidate the resultId (see the discussion at:
   * https://github.com/VizierDB/vizier-scala/wiki/DevGuide-CellStates#state-definitions)
   */
  def updateResult(transitions: Seq[StateTransition]): SQLSyntax = 
  {
    val result = 
      if(transitions.isEmpty){
        sqls"resultId"
      } else {
        transitions.map { _.resultUpdateSyntax }
                   .foldLeft(sqls"case") { _ + sqls" " + _ } + sqls" else result_id end"
      }
    logger.trace(s"Result Transitions for: \n${transitions.mkString("\n")}\nare: ${result}")
    return result
  }

}