package info.vizierdb.spark

import com.typesafe.scalalogging.LazyLogging

import org.apache.spark.sql.{ SparkSession, DataFrame, Row, Dataset }
import org.apache.spark.sql.catalyst.{ QueryPlanningTracker, AliasIdentifier }
import org.apache.spark.sql.catalyst.plans.logical.{ LogicalPlan, SubqueryAlias, View }
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.execution.QueryExecution

import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.catalog.{ CatalogTable, CatalogStorageFormat, CatalogTableType }
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.expressions.{ Expression, PlanExpression }
import org.apache.spark.sql.catalyst.analysis.UnresolvedFunction

import info.vizierdb.api.FormattedError
import info.vizierdb.Vizier

/**
 * Utilities for running Spark SQL queries with a post-processing step injected between
 * the parser and the analysis phase.
 */
object InjectedSparkSQL
  extends LazyLogging
{
  lazy val spark = Vizier.sparkSession

  object getViewReferences extends GetDependencies[String]
  {
    val byPlan: PartialFunction[LogicalPlan, Set[String]] = 
      { case UnresolvedRelation(Seq(identifier), options, isStreaming) => 
              Set(identifier.toLowerCase) }
    val byExpression: PartialFunction[Expression, Set[String]] = 
      { case s => Set[String]() }  
  }

  object getFunctionReferences extends GetDependencies[String]
  {
    val byPlan: PartialFunction[LogicalPlan, Set[String]] = 
      { case p => Set[String]() }
    val byExpression: PartialFunction[Expression, Set[String]] = 
      { case UnresolvedFunction(name,_,_,_, _) => 
          Set(name.mkString(".").toLowerCase) }
  }

  def parse(sqlText: String): LogicalPlan =
  {
    // ~= Spark's SparkSession.sql()
    val tracker = new QueryPlanningTracker
    val logicalPlan = spark.sessionState.sqlParser.parsePlan(sqlText)
    logger.trace(logicalPlan.toString())
    return logicalPlan    
  }

  def getUserDefinedFunctionReferences(logicalPlan: LogicalPlan): Set[String] =
    getFunctionReferences(logicalPlan)
      .filterNot { spark.catalog.functionExists(_) }

  def getDependencies(sqlText: String): (Set[String], Set[String]) =
  {
    val logicalPlan = parse(sqlText)
    return (
      getViewReferences(logicalPlan),
      getUserDefinedFunctionReferences(logicalPlan)
    )
  }


  /**
   * Run the specified query with supplemental views
   *
   * @param  sqlText                 The query to run
   * @param  tableMappings           The (case-insensitive) views to substitute in the query
   * @param  allowMappedTablesOnly   If true, only allow tables that appear in tableMappings (default: false)
   * @return                         A DataFrame (analogous to SparkSession.sql) and a set of references
   */
  def apply(
    sqlText: String, 
    tableMappings: Map[String,() => DataFrame] = Map(), 
    allowMappedTablesOnly: Boolean = false,
    functionMappings: Map[String, Seq[Expression] => Expression] = Map.empty
  ): DataFrame =
  {
    val logicalPlan = parse(sqlText)
    
    // The magic happens here.  We rewrite the query to inject our own 
    // table rewrites
    val rewrittenPlan = rewrite(
      logicalPlan, 
      tableMappings.map { case (k, v) => k.toLowerCase() -> v }.toMap,// make source names case insensitive
      functionMappings.map { case (k, v) => k.toLowerCase() -> v }.toMap,// make source names case insensitive
      allowMappedTablesOnly
    )

    logger.trace(rewrittenPlan.toString())
    // ~= Spark's Dataset.ofRows()
    val qe = new QueryExecution(spark, rewrittenPlan)
    logger.trace(qe.analyzed.toString())


    qe.assertAnalyzed()
    return (
      new Dataset[Row](spark, qe.analyzed, RowEncoder(qe.analyzed.schema)),
    )
  }

  /**
   * Rewrite the specified logical plan with a set of supplemental views
   *
   * @param  sqlText                 The query to run
   * @param  tableMappings           The (case-insensitive) views to substitute in the query
   * @param  allowMappedTablesOnly   If true, only allow tables that appear in tableMappings (default: false)
   * @return                         A logical plan with views replaced and a sets of all views and functions referenced
   */
  def rewrite(
    plan: LogicalPlan, 
    tableMappings: Map[String, () => DataFrame] = Map(), 
    functionMappings: Map[String, Seq[Expression] => Expression] = Map(), 
    allowMappedTablesOnly: Boolean = false
  ): LogicalPlan =
  {
    def recur(target: LogicalPlan) = {
      rewrite(
        plan = target, 
        tableMappings = tableMappings, 
        functionMappings = functionMappings,
        allowMappedTablesOnly = allowMappedTablesOnly
      )
    }

    logger.debug(s"Rewriting...\n$plan")
    val ret = 
      plan.transformUp { 
        case original @ UnresolvedRelation(Seq(identifier), options, isStreaming) => 
          tableMappings.get(identifier.toLowerCase()) match {
            // If we only allow mapped tables, throw a nice user-friendly error
            case None if allowMappedTablesOnly => 
              throw new FormattedError(
                s"Unknown table $identifier (Available tables: ${tableMappings.keys.mkString(", ")})",
              )

            // If we allow any tables, pass through and let spark catch any problems
            case None => original

            // Finally, if we have a mapping, use it!
            case Some(constructor) => 
              // It's *critical* that we use the *analyzed* version of the query here.  Otherwise,
              // we end up with multiple copies of the same name floating around which makes
              // spark righteously upset.
              val child = constructor().queryExecution.analyzed

              // Wrap the child in a SubqueryAlias to allow the SQL query to refer to the stored 
              // table by its aliased name.
              new SubqueryAlias(
                AliasIdentifier(identifier.toLowerCase()),
                child
              )
          }
      }.transformAllExpressions { 
        case nested: PlanExpression[_] => 
          nested.plan match { 
            case nestedPlan: LogicalPlan => 
              nested.asInstanceOf[PlanExpression[LogicalPlan]]
                    .withNewPlan(recur(nestedPlan))
            case _ => nested
          }
        case UnresolvedFunction(name, args, isDistinct, filter, ignoreNulls) 
          if functionMappings contains name.mkString(".").toLowerCase =>
            logger.debug(s"Rewriting UDF ${name.mkString(".")}")
            val ret = functionMappings(name.mkString(".").toLowerCase)(args)
            logger.debug(s"... to: $ret (${ret.getClass()}")
            ret
      }

    logger.trace(s"Done rewriting!\n$ret")
    return ret
  }
}