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
import org.apache.spark.sql.catalyst.plans.{ LeftSemi, LeftAnti }
import org.apache.spark.sql.types._
import info.vizierdb.spark.ArtifactProvenance

/**
 * Plan annotator that adds an Array[String] column containing the artifact IDs from
 * vizier_prov_* SubqueryAlias nodes for each row.
 *
 * Analogous to CaveatMessagesInPlan but uses view-definition (SubqueryAlias) based
 * provenance instead of ApplyCaveat nodes.  For leaf nodes that carry a physical
 * __vizier_provenance__ column (written before a Parquet round-trip), that column
 * is used directly as the per-row annotation so no re-stamping is needed on read-back.
 */
class ViewProvenanceInPlan(
  annotationName: String = ViewProvenanceInPlan.ANNOTATION
) {
  private val emptyArray: Expression =
    Literal.create(Seq.empty[String], ArrayType(StringType, containsNull = false))
  private val arrayType: DataType = ArrayType(StringType)

  private def foldArrayConcat(exprs: Expression*): Expression = exprs.toList match {
    case Nil        => emptyArray
    case List(only) => only
    case multiple   => Concat(multiple)
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
   * Annotate the plan.  Returns (newPlan, attrRef) where attrRef points at the
   * added Array[String] annotation column in newPlan's output.
   */
  def annotate(plan: LogicalPlan): (LogicalPlan, AttributeReference) =
    plan match {

      // Provenance alias: extract artifact IDs from the alias name suffix
      case sa: SubqueryAlias if sa.identifier.name.startsWith(ArtifactProvenance.VIEW_PREFIX) =>
        val (annotatedChild, childAttr) = annotate(sa.child)
        val artIds = sa.identifier.name
          .drop(ArtifactProvenance.VIEW_PREFIX.length)
          .split("_")
          .flatMap(s => scala.util.Try(s.toLong).toOption)
          .map(id => Literal(id.toString): Expression)
          .toSeq
        val thisMsg  = if (artIds.isEmpty) emptyArray else CreateArray(artIds)
        val combined = foldArrayConcat(childAttr, thisMsg)
        val alias    = Alias(combined, annotationName)()
        val attr     = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        val newPlan = Project(
          annotatedChild.output.filterNot(_.exprId == childAttr.exprId) :+ alias,
          SubqueryAlias(sa.identifier, annotatedChild)
        )
        (newPlan, attr)

      // Leaf node: if __vizier_provenance__ physical column exists use it directly
      // (Parquet round-trip case); otherwise emit empty array
      case leaf: LeafNode =>
        leaf.output.collectFirst {
          case ar: AttributeReference if ar.name == ArtifactProvenance.PROVENANCE_COLUMN => ar
        } match {
          case Some(provAttr) =>
            (leaf, provAttr)
          case None =>
            val alias = Alias(emptyArray, annotationName)()
            val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
            (Project(leaf.output :+ alias, leaf), attr)
        }

      // Filter: pass through (handles non-provenance filters as well as old-style caveats)
      case Filter(condition, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        (Filter(condition, annotatedChild), childAttr)

      // Project: thread annotation through the projection list
      case Project(projectList, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        val alias = Alias(childAttr, annotationName)()
        val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
        (Project(projectList :+ alias, annotatedChild), attr)

      // Union: each row comes from exactly one branch; expose the first child's attr
      case Union(children, byName, allowMissingCol) =>
        val annotated = children.map(annotate)
        val (newChildren, childAttrs) = annotated.unzip
        (Union(newChildren, byName, allowMissingCol), childAttrs.head)

      // Join: LeftSemi/LeftAnti output only the left side, so the right annotation is
      // not reachable from plan.output — use left annotation only for those join types.
      case Join(left, right, joinType, condition, hint) =>
        val (annotatedLeft, leftAttr) = annotate(left)
        joinType match {
          case LeftSemi | LeftAnti =>
            val alias = Alias(leftAttr, annotationName)()
            val attr  = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
            val newPlan = Project(
              plan.output :+ alias,
              Join(annotatedLeft, right, joinType, condition, hint)
            )
            (newPlan, attr)
          case _ =>
            val (annotatedRight, rightAttr) = annotate(right)
            val combined = foldArrayConcat(leftAttr, rightAttr)
            val alias    = Alias(combined, annotationName)()
            val attr     = AttributeReference(alias.name, arrayType)(exprId = alias.exprId)
            val newPlan = Project(
              plan.output :+ alias,
              Join(annotatedLeft, annotatedRight, joinType, condition, hint)
            )
            (newPlan, attr)
        }

      // Aggregate: collect per-row arrays, flatten and deduplicate per group
      case Aggregate(groupingExpressions, aggregateExpressions, child) =>
        val (annotatedChild, childAttr) = annotate(child)
        val msgAlias = Alias(aggregateCollectMessages(childAttr), annotationName)()
        val attr     = AttributeReference(msgAlias.name, arrayType)(exprId = msgAlias.exprId)
        (Aggregate(groupingExpressions, aggregateExpressions :+ msgAlias, annotatedChild), attr)

      // Single-child pass-through (Sort, Sample, non-provenance SubqueryAlias, ReturnAnswer, …)
      case _ if plan.children.size == 1 =>
        val (annotatedChild, childAttr) = annotate(plan.children.head)
        (plan.withNewChildren(Seq(annotatedChild)), childAttr)

      // Multi-child fallthrough
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
   * Returns a new DataFrame identical to `df` but with an extra Array[String] column
   * (named [[annotationName]]) containing the per-row artifact IDs drawn from
   * vizier_prov_* view aliases (or the physical __vizier_provenance__ column for
   * Parquet-backed DataFrames).
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

object ViewProvenanceInPlan {
  val ANNOTATION = "__MIMIR_PROVENANCE_MESSAGES__"
}
