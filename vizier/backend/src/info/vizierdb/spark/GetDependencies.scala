/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
