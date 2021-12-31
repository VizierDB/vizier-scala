package info.vizierdb.spark


import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.catalyst.plans.logical.{ LogicalPlan, View }
import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation
import org.apache.spark.sql.catalyst.plans.logical.SubqueryAlias
import org.apache.spark.sql.catalyst.expressions.{ Expression, PlanExpression }

abstract class GetDependencies[T]
{
  def apply(df: DataFrame): Set[T] =
    apply(df.queryExecution.analyzed)

  def apply(plan: LogicalPlan): Set[T] =
    plan.collect(byPlan).flatten.toSet ++
      plan.collect { 
              case x => x.expressions.flatMap { apply(_) }.toSet
            }.flatten.toSet

  def apply(expression: Expression): Set[T] =
    expression.collect(byExpression).flatten.toSet ++
      expression.collect { 
        case nested: PlanExpression[_] => 
          nested.plan match { 
            case nestedPlan: LogicalPlan => 
              apply(nested.asInstanceOf[PlanExpression[LogicalPlan]]
                          .plan)
            case _ => Set[T]()
          }
      }.flatten.toSet

  val byPlan: PartialFunction[LogicalPlan, Set[T]]
  val byExpression: PartialFunction[Expression, Set[T]]

}
