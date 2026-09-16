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
  val FAMILY = "vizier_provenance"

  /**
   * Stamp every row of `df` with `artifactId` by injecting an ApplyCaveat node into
   * the Spark logical plan.  The caveat uses family=FAMILY and carries the artifact ID
   * as its message.  No physical column is added; the annotation is invisible to user
   * code and survives filter/project/join transformations automatically.
   */
  def stamp(df: org.apache.spark.sql.DataFrame, artifactId: Long): org.apache.spark.sql.DataFrame =
  {
    import org.mimirdb.caveats.implicits._
    import org.apache.spark.sql.functions.lit
    df.filter(lit(true).caveat(lit(artifactId.toString), FAMILY)())
  }

  /**
   * Remove all ApplyCaveat nodes from the plan (including any non-provenance caveats).
   * Use before writing to Parquet or before handing a DF to code that must not see caveats.
   */
  def strip(df: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame =
    org.mimirdb.caveats.Caveats.strip(df)

  /**
   * Walk the analyzed logical plan and collect every artifact ID that was stamped into
   * this DataFrame via provenance caveats.  Returns an empty set if none were found or
   * if plan analysis fails.
   */
  def artifactIds(df: org.apache.spark.sql.DataFrame): Set[Long] =
  {
    import org.mimirdb.caveats.ApplyCaveat
    import org.apache.spark.sql.catalyst.expressions.Literal
    val ids = scala.collection.mutable.Set[Long]()
    try {
      df.queryExecution.analyzed.foreachUp { plan =>
        plan.expressions.foreach(_.foreach {
          case ap: ApplyCaveat if ap.family.contains(FAMILY) =>
            ap.message match {
              case Literal(v, _) => scala.util.Try(v.toString.toLong).foreach(ids += _)
              case _ => ()
            }
          case _ => ()
        })
      }
    } catch { case _: Throwable => () }
    ids.toSet
  }
}
