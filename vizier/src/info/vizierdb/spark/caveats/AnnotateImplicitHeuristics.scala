package info.vizierdb.spark.caveats

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.catalyst.plans.logical.{ LogicalPlan, View }
import org.apache.spark.sql.catalyst.expressions.{ Cast, Attribute }
import org.apache.spark.sql.catalyst.catalog.CatalogTable
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.lenses.CaveatedCast


/**
 * This function injects caveats around normal SQL operations with known failure cases.
 *
 * Operations rewritten include:
 * - CAST -> caveatedCast
 * 
 * This function is designed to be run on standalone SQL.  It will not recur down through views or 
 * similar named subqueries.
 */
object AnnotateImplicitHeuristics
  extends LazyLogging
{
  def apply(df: DataFrame): DataFrame =
  {
    new DataFrame(
      df.queryExecution.sparkSession,
      apply(df.queryExecution.analyzed),
      RowEncoder(df.schema)
    )
  }

  def apply(query: LogicalPlan): LogicalPlan =
  {
    logger.trace(s"Annotate Implicit Heuristics of:\n$query")
    query match {
      case View(desc: CatalogTable, isTempView: Boolean, child: LogicalPlan) => query
      case _ => {
        query.transformExpressionsUp { 
          case c@Cast(child, t, tzinfo, ansiEnabled) => {
            logger.trace(s"Rewriting cast: $c")
            CaveatedCast(child, t, tzinfo = tzinfo)
          }
        }
      }
    }
  }
}