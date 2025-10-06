package info.vizierdb.spark

import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.expressions.aggregate._
import org.apache.spark.sql.types._
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.Filter
import org.apache.spark.sql.catalyst.plans.logical.Project

object expressionLogic
{
  def attributesOfExpression(e: Expression):Set[Attribute] =
  {
    e match { 
      case a: Attribute => Set(a)
      case s: SubqueryExpression => 
      {
        val isPlanAttribute = s.plan.output.map { _.exprId }.toSet
        // print(s"PLAN ATTRIBUTES: $isPlanAttribute")
        (
          s.children
           .flatMap { attributesOfExpression(_) }
           .filter { a => !isPlanAttribute(a.exprId) }
           .toSet 
        )
      }
      case _ => e.children.flatMap { attributesOfExpression(_) }.toSet
    }
  }

  // /**
  //   * Construct a new expression tree by applying [[f]] to all nodes in the
  //   * expression tree rooted at [[e]].
  //   *  @param e input expression
  //   *  @param f function the constructs the new expression (
  //   */
  // def mutateExpr(e: Expression, f: Expression => Expression) = {

  // }

  def negate(e: Expression): Expression =
    e match {
      case Not(n) => n
      case Or(l, r) => And(negate(l), negate(r))
      case And(l, r) => Or(negate(l), negate(r))
      case Literal(x, BooleanType) => Literal(!x.asInstanceOf[Boolean])
      case _ => Not(e)
    }
  def foldOr(e:Expression*) = fold(e, true)
  def foldAnd(e:Expression*) = fold(e, false)
  def foldIf(i:Expression)(t: Expression)(e: Expression) =
    e match {
      case Literal(true, BooleanType) => t
      case Literal(false, BooleanType) => e
      case _ => If(i, t, e)
    }

  def aggregateBoolOr(e:Expression) =
    e match {
      case Literal(false, BooleanType) => Literal(false)
      case _ => //BoolOr("bool_or", e)
        GreaterThan(
          AggregateExpression(
            Sum(foldIf(e){ Literal(1) }{ Literal(0) }),
            Complete,
            false,
            None,
            NamedExpression.newExprId
          ),
          Literal(0)
        )
    }

  def wrapAgg(e: AggregateFunction, distinct: Boolean = false) = {
    AggregateExpression(
      e,
      Complete,
      distinct,
      None,
      NamedExpression.newExprId
    )
  }

  def simplify(e: Expression): Expression =
  {
    // println(s"SIMPLIFY: $e")
    if(e.references.isEmpty){ Literal(e.eval(), e.dataType) }
    else { 
      e.mapChildren { 
        case r:RuntimeReplaceable => simplify(r.replacement)
        case x => simplify(x) 
      } 
    }
  }

  // def inline(e: Expression, projectionList: Seq[NamedExpression]): Expression =
  //   inline(e, projectionList.map { 
  //               case a@Alias(child, name) => a.exprId -> child
  //               case expr => expr.exprId -> expr 
  //             }.toMap)

  // def inline(e: Expression, replacements: Map[ExprId,Expression]): Expression =
  // {
  //   e match {
  //     // Base case: An attribute that can be replaced
  //     case a:Attribute => replacements.getOrElse(a.exprId, a)

  //     // RuntimeReplaceable expressions are internally equivalent to/replacable
  //     // with their _.child field.  The outer shell may have other subexpressions,
  //     // but these are only ever used for display purposes.  Unfortunately, 
  //     // _.mapChildren only gets applied to the _.child field, so inlining ends
  //     // up creating expression trees that make no sense when printed.  In the
  //     // interest of making debugging easier, just flatten out the expression
  //     // here and now.  
  //     case r:RuntimeReplaceable => inline(r.replacement, replacements)

  //     // Exists is... odd.  If we need to inline into an Exists expression
  //     // we need to replace all of the expression references in the subquery
  //     // as well.  Note: we also need to be mindful of the possibility that
  //     // the subquery may have 
  //     case Exists(subquery, outerAttrs, exprId, joinCond) =>
  //     {
  //       val (
  //         newOuterAttributesNested, 
  //         innerReplacementsNested
  //       ):(Seq[Iterable[Expression]], Seq[Seq[(ExprId, Expression)]]) = 
  //         outerAttrs.map { 
  //           case n:NamedExpression if replacements.contains(n.exprId) => 
  //             val theReplacement = replacements(n.exprId)
  //             (
  //               theReplacement.references.map { _.toAttribute }:Iterable[Expression],
  //               Seq(
  //                 n.exprId ->
  //                   theReplacement.transform {
  //                     case a:AttributeReference => 
  //                       OuterReference(a)
  //                   }
  //               )
  //             )
  //           case e: Expression => 
  //             (
  //               Seq(e),
  //               Seq.empty
  //             )
  //         }.unzip

  //       val innerReplacements = innerReplacementsNested.flatten.toMap

  //       Exists(
  //         subquery.transformAllExpressions {
  //           case OuterReference(x) if innerReplacements contains(x.exprId) => 
  //             innerReplacements(x.exprId)
  //         },
  //         newOuterAttributesNested.flatten,
  //         exprId,
  //         joinCond
  //       )
  //     }


  //     // Recursive case: Replace all descendants.
  //     case _ => e.mapChildren { inline(_, replacements) }
  //   }
  // }

  def isAggregate(e: Expression): Boolean =
  {
    e match {
      case _:AggregateExpression => true
      case _ => e.children.exists { isAggregate(_) }
    }
  }

  private def fold(
    conditions: Seq[Expression],
    disjunctive: Boolean = true
  ): Expression =
    conditions.filter { !_.equals(Literal(!disjunctive)) } match {
      // Non-caveatted node.
      case Seq() => Literal(!disjunctive)

      // One potential caveat.
      case Seq(condition) => condition

      // Always caveatted node
      case c if c.exists { _.equals(Literal(disjunctive)) } => Literal(disjunctive)

      // Multiple potential caveats
      case c =>
        val op = (if(disjunctive) { Or(_,_) } else { And(_,_) })
        c.tail.foldLeft(c.head) { op(_,_) }
    }

  def splitAnd(condition: Expression): Seq[Expression] = {
    condition match {
      case And(cond1, cond2) =>
        splitAnd(cond1) ++ splitAnd(cond2)
      case other => other :: Nil
    }
  }

  def splitOr(condition: Expression): Seq[Expression] = {
    condition match {
      case Or(cond1, cond2) =>
        splitOr(cond1) ++ splitOr(cond2)
      case other => other :: Nil
    }
  }

  /**
   * Construct an Exists test
   * @param   subplan     The logical plan to build the exists around
   * @param   condition   The condition to filter on
   * @return              An [[Exists]] expression
   * 
   * [[Attribute]]s in the subplan will be renamed to fresh ones
   * 
   * [[Attribute]] references not declared by the subplan will be assumed to
   * come from the outside
   */
  def buildExists(
    subplan: LogicalPlan, 
    condition: Expression, 
    exprId: ExprId = NamedExpression.newExprId
  ): Exists =
  {
    var innerAttributes:Set[Attribute] = 
      subplan.output.toSet
    var outerAttributes:Set[Attribute] = 
      condition.references.map { _.toAttribute }.toSet -- innerAttributes

    val innerAttributeRenamings = 
      innerAttributes.map { a => 
        a -> Alias(a, a.name)()
      }

    val renamings = 
      innerAttributeRenamings.map { case (attr, alias) =>
        attr.exprId -> 
          AttributeReference(alias.name, alias.dataType, alias.nullable)(exprId = alias.exprId)
      }.toMap ++ 
      outerAttributes.map { attr => 
        attr.exprId -> OuterReference(attr)
      }.toMap

    Exists(
      Filter(
        condition.transformUp {
          case OuterReference(e) => e
          case a:AttributeReference => renamings(a.exprId)
        },
        Project(
          innerAttributeRenamings.toSeq.map { _._2 },
          subplan
        )
      ),
      outerAttributes.toSeq,
      exprId = exprId
    )
  }
}
