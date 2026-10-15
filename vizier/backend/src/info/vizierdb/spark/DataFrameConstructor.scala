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
package info.vizierdb.spark


import play.api.libs.json.{ JsValue, Format }
import org.apache.spark.sql.{ SparkSession, DataFrame } 
import info.vizierdb.types.Identifier
import org.apache.spark.sql.types.StructField
import info.vizierdb.catalog.Artifact

/**
 * A generic view definition for the Mimir Catalog.  Used by Catalog.put and related operations
 */
trait DataFrameConstructor 
{
  /**
   * Construct the DataFrame represented by this Constructor
   * 
   * @param spark     The Spark session in the context of which to create the DataFrame
   * @param context   Lazy constructors for dependent dataframes, organized by name.  
   * @return          The DataFrame
   *
   * This function returns a DataFrame intended for computing results, and
   * may materialize intermediates.  If the intent is to do static analysis,
   * use provenance().
   * 
   * When `Catalog.put` is called, one of its parameters is a list of dependencies.  Only dependent
   * tables will be present in the context.
   */
  def construct(context: Identifier => Artifact): DataFrame

  /**
   * Return the full provenance of the DataFrame.
   * 
   * @param spark     The Spark session in the context of which to create the DataFrame
   * @param context   Lazy constructors for dependent dataframes, organized by name.  
   * @return          The DataFrame
   *
   * This function returns a DataFrame intended for static analysis, and may
   * involve more computation than necessary.  If the intent is to compute
   * values, use construct()
   */
  def provenance(context: Identifier => Artifact): DataFrame

  /**
   * The companion object including a deserialization method.
   * @return          The class name of an object extending the DataFrameConstructorCodec trait
   *
   * By default, this assumes a companion object of the same name as the base class.
   */
  def deserializer = getClass.getName + "$"

  /**
   * Return the set of identifiers **actually** referenced by this df
   */
  def dependencies: Set[Identifier]

  /**
   * Return the schema of the generated dataframe
   * 
   * (This should be cached, independently of upstream dataframes)
   */
  def schema: Seq[StructField]

}

trait DataFrameConstructorCodec
{
  def apply(j: JsValue): DataFrameConstructor
}

trait DefaultProvenance
{
  def construct(context: (Identifier => Artifact)): DataFrame
  def provenance(context: (Identifier => Artifact)): DataFrame =
    construct(context)
}

object ArtifactProvenance
{
  /**
   * Prefix used for SubqueryAlias node names that carry provenance.
   * e.g. "vizier_prov_42" stamps artifact ID 42; "vizier_prov_42_43" stamps both.
   */
  val VIEW_PREFIX = "vizier_prov_"

  /**
   * Physical column written to Parquet that stores the per-row Array[String] of
   * artifact IDs.  ViewProvenanceInPlan reads this directly from leaf scan nodes so
   * LoadConstructor does not need to re-stamp rows on read-back.
   */
  val PROVENANCE_COLUMN = "__vizier_provenance__"

  /**
   * Stamp every row of `df` with `artifactId` by wrapping the plan in a SubqueryAlias
   * whose name encodes the artifact ID.  Any existing PROVENANCE_COLUMN is dropped
   * first so it does not appear in the user-visible schema; ViewProvenanceInPlan can
   * still reach it through the underlying scan node.
   */
  def stamp(df: org.apache.spark.sql.DataFrame, artifactId: Long): org.apache.spark.sql.DataFrame =
  {
    val base = if (df.columns.contains(PROVENANCE_COLUMN)) df.drop(PROVENANCE_COLUMN) else df
    base.as(s"${VIEW_PREFIX}${artifactId}")
  }

  /**
   * Add per-row provenance tracking to the DataFrame before writing to Parquet.
   * Computes an Array[String] column (PROVENANCE_COLUMN) by walking the analyzed plan
   * for vizier_prov_* SubqueryAlias nodes (or reading the physical column from leaf
   * scans for Parquet-backed DataFrames).
   */
  def addPerRowProvenanceColumn(df: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame =
  {
    import info.vizierdb.spark.caveats.ViewProvenanceInPlan
    new ViewProvenanceInPlan(PROVENANCE_COLUMN)(df)
  }

  /**
   * Remove all vizier_prov_* SubqueryAlias nodes from the analyzed plan.
   * Use before writing to Parquet or before handing a DF to code that must not see
   * provenance wrappers.
   */
  def strip(df: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame =
  {
    import org.apache.spark.sql.catalyst.plans.logical.{ LogicalPlan, SubqueryAlias }
    import org.apache.spark.sql.catalyst.encoders.RowEncoder
    import org.apache.spark.sql.types.StructType

    def stripPlan(plan: LogicalPlan): LogicalPlan = plan match {
      case sa: SubqueryAlias if sa.identifier.name.startsWith(VIEW_PREFIX) =>
        stripPlan(sa.child)
      case _ =>
        plan.withNewChildren(plan.children.map(stripPlan))
    }

    val stripped = stripPlan(df.queryExecution.analyzed)
    new org.apache.spark.sql.DataFrame(
      df.queryExecution.sparkSession,
      stripped,
      RowEncoder(StructType(df.schema.fields))
    )
  }

  /**
   * Walk the analyzed logical plan and collect every artifact ID that was stamped into
   * this DataFrame via vizier_prov_* SubqueryAlias nodes.  Returns an empty set if
   * none were found or if plan analysis fails.
   */
  def artifactIds(df: org.apache.spark.sql.DataFrame): Set[Long] =
  {
    import org.apache.spark.sql.catalyst.plans.logical.SubqueryAlias
    val ids = scala.collection.mutable.Set[Long]()
    try {
      df.queryExecution.analyzed.foreachUp { (node: org.apache.spark.sql.catalyst.plans.logical.LogicalPlan) =>
        node match {
          case sa: SubqueryAlias if sa.identifier.name.startsWith(VIEW_PREFIX) =>
            sa.identifier.name.drop(VIEW_PREFIX.length).split("_").foreach { part =>
              scala.util.Try(part.toLong).foreach(ids += _)
            }
          case _ =>
        }
      }
    } catch { case _: Throwable => () }
    ids.toSet
  }
}
