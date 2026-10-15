/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.spark.caveats

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.expressions.aggregate.{ AggregateExpression, CollectList, Complete }
import org.apache.spark.sql.catalyst.plans.logical._
import org.apache.spark.sql.types._
import org.mimirdb.caveats.ApplyCaveat

/**
 * Plan annotator that adds an Array[String] column containing the messages from
 * caveats of a specified family for each row.
 *
 * Analogous to CaveatExistsInPlan but instead of a boolean "is this row caveated",
 * produces an Array[String] of all caveat messages from the target family that
 * apply to each row.  Used for per-row provenance attribution that survives
 * Parquet round-trips.
 */
class CaveatMessagesInPlan(
  family: String,
  annotationName: String = CaveatMessagesInPlan.ANNOTATION
) {
  private val emptyArray: Expression = Literal.create(Seq.empty[String], ArrayType(StringType, containsNull = false))
  private val arrayType: DataType    = ArrayType(StringType)

  private def foldArrayConcat(exprs: Expression*): Expression = {
    val nonEmpty = exprs.filter {
      case ca: CreateArray => ca.children.nonEmpty
      case _ => true
    }
    nonEmpty.toList match {
      case Nil        => Literal.create(Seq.empty[String], ArrayType(StringType, containsNull = false))
      case List(only) => only
      case multiple   => Concat(multiple)
    }
  }

  private def aggregateCollectMessages(e: Expression): Expression =
    ArrayDistinct(
      Flatten(
        AggregateExpression(
          CollectList(e),
          Complete,
          isDistinct = false,
          filter = None,
          resultId = NamedExpression.newExprId
        )
      )
    )

  /**
   * Annotate the plan.  Returns (newPlan, attrRef) where attrRef is an
   * AttributeReference pointing at the added Array[String] annotation column
   * in newPlan's output.
   */
  def annotate(plan: LogicalPlan): (LogicalPlan, AttributeReference) =
    plan match {

      // Leaf: no caveats — emit empty array
      case _: LeafNode =>
        val alias = Alias(emptyArray, annotationName)()
        val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        (Project(plan.output :+ alias, plan), attr)

      // Provenance-family stamp: Filter(ApplyCaveat(_, msg, Some(family), _, _, cond), child)
      // Add the message to the per-row array when the caveat condition is true.
      case Filter(ap @ ApplyCaveat(_, message, Some(f), _, _, condition), child) if f == family =>
        val (annotatedChild, childAttr) = annotate(child)
        val thisMsg = If(condition, CreateArray(Seq(Cast(message, StringType))), emptyArray)
        val combined = foldArrayConcat(childAttr, thisMsg)
        val alias    = Alias(combined, annotationName)()
        val attr     = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        val newPlan = Project(
          annotatedChild.output.filterNot(_.exprId == childAttr.exprId) :+ alias,
          Filter(ap, annotatedChild)
        )
        (newPlan, attr)

      // Filter for a different family or a plain condition: pass through
      case Filter(condition, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        (Filter(condition, annotatedChild), childAttr)

      // Project: must thread the annotation through the projection list
      case Project(projectList, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        val alias = Alias(childAttr, annotationName)()
        val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        (Project(projectList :+ alias, annotatedChild), attr)

      // Union: each row comes from exactly one branch, so the annotation passes through
      // naturally.  Union.output == children.head.output, so the first child's annotation
      // attribute is the one we expose.
      case Union(children, byName, allowMissingCol) =>
        val annotated = children.map(annotate)
        val (newChildren, childAttrs) = annotated.unzip
        (Union(newChildren, byName, allowMissingCol), childAttrs.head)

      // Join: concatenate annotations from both sides
      case Join(left, right, joinType, condition, hint) =>
        val (annotatedLeft,  leftAttr)  = annotate(left)
        val (annotatedRight, rightAttr) = annotate(right)
        val combined = foldArrayConcat(leftAttr, rightAttr)
        val alias    = Alias(combined, annotationName)()
        val attr     = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        val newPlan = Project(
          plan.output :+ alias,
          Join(annotatedLeft, annotatedRight, joinType, condition, hint)
        )
        (newPlan, attr)

      // Aggregate: collect per-row message arrays, flatten to one array per group
      case Aggregate(groupingExpressions, aggregateExpressions, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        val msgAlias = Alias(aggregateCollectMessages(childAttr), annotationName)()
        val attr     = AttributeReference(msgAlias.name, arrayType)(exprId = msgAlias.exprId)
        (Aggregate(groupingExpressions, aggregateExpressions :+ msgAlias, annotatedChild), attr)

      // Pass-through operators (Sort, Sample, SubqueryAlias, ReturnAnswer, …):
      // the single child's schema propagates naturally through these nodes.
      case _ if plan.children.size == 1 =>
        val (annotatedChild, childAttr) = annotate(plan.children.head)
        (plan.withNewChildren(Seq(annotatedChild)), childAttr)

      // Multi-child fall-through: merge annotations with concat
      case _ =>
        val (newChildren, childAttrs) = plan.children.map(annotate).unzip
        val combined = childAttrs match {
          case Seq()       => emptyArray
          case Seq(single) => single: Expression
          case multiple    => foldArrayConcat(multiple: _*)
        }
        val alias = Alias(combined, annotationName)()
        val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        (Project(plan.output :+ alias, plan.withNewChildren(newChildren)), attr)
    }

  /**
   * Returns a new DataFrame identical to `df` but with an extra Array[String]
   * column (named [[annotationName]]) containing the per-row messages for the
   * target caveat family.
   */
  def apply(df: DataFrame): DataFrame = {
    val (annotatedPlan, _) = annotate(df.queryExecution.analyzed)
    new DataFrame(
      df.queryExecution.sparkSession,
      annotatedPlan,
      RowEncoder(StructType(df.schema.fields :+ StructField(annotationName, arrayType)))
    )
  }
}

object CaveatMessagesInPlan {
  val ANNOTATION = "__MIMIR_PROVENANCE_MESSAGES__"
}
