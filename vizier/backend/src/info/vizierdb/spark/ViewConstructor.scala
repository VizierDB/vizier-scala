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

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.catalyst.expressions.Expression
import info.vizierdb.commands.python.PythonProcess
import info.vizierdb.spark.caveats.AnnotateImplicitHeuristics
import org.mimirdb.caveats.lifting.ResolveLifts
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import breeze.linalg.View
import info.vizierdb.Vizier
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructType
import info.vizierdb.VizierException
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.types.DataType

case class ViewConstructor(
  datasets: Map[String, Identifier],
  // For functions, we need to cache the function mime type and text to avoid reentrancy.
  functions: Map[String, (Identifier, String, String)],
  query: String,
  projectId: Identifier,
  schema: Seq[StructField],
  variables: Option[Map[String, Identifier]],
) extends DataFrameConstructor
  with DefaultProvenance
{

  lazy val lowerCaseDatasets = datasets.map { case (k, v) => k.toLowerCase -> v }.toMap
  lazy val lowerCaseFunctions = functions.map { case (k, v) => k.toLowerCase -> v }.toMap
  lazy val lowerCaseVariables = variables.getOrElse { Map.empty }. map { case (k, v) => k.toLowerCase -> v }.toMap

  def construct(context: Identifier => Artifact): DataFrame =
  {
    var df = ViewConstructor.buildBase(
                query = query,
                tableMappings = datasets.mapValues { id => () => context(id).dataframeFromContext(context) },
                functions = functions,
                variables = variables.getOrElse { Map.empty }.mapValues { id => () => Literal(context(id).parameter.nativeValue) }
              )
    df = AnnotateImplicitHeuristics(df)
    df = ResolveLifts(df)
    return df 
  }

  lazy val (viewDeps, fnDeps, varDeps): (Set[String], Set[String], Set[String]) =
      InjectedSparkSQL.getDependencies(query)
  lazy val dependencies:Set[Identifier] = {
    viewDeps.map { _.toLowerCase }.map { x =>
        lowerCaseDatasets.getOrElse(x, 
          throw new VizierException(s"Internal Error: Undefined view $x; looking in: ${datasets.keys.mkString(", ")}")
        )
      }.toSet ++
    fnDeps.map { _.toLowerCase }.map { x => 
        lowerCaseFunctions.getOrElse(x,
          throw new VizierException(s"Internal Error: Undefined function $x; looking in: ${functions.keys.mkString(", ")}")
        )._1 }.toSet ++
    varDeps.map { _.toLowerCase }.map { x => 
        lowerCaseVariables.getOrElse(x,
          throw new VizierException("Internal Error: Undefined variable $"+s"$x; looking in: ${variables.getOrElse { Map.empty }.keys.mkString(", ")}")
        ) }.toSet
  }
}
object ViewConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[ViewConstructor] = Json.format
  def apply(j: JsValue) = j.as[ViewConstructor]
  def apply(
    datasets: Map[String, Identifier],
    // For functions, we need to cache the function mime type and text to avoid reentrancy.
    functions: Map[String, (Identifier, String, String)],
    variables: Map[String, Identifier],
    query: String,
    projectId: Identifier,
    datasetSchemas: Identifier => Seq[StructField],
    variableTypes: Identifier => DataType,
  ): ViewConstructor = 
  {
    ViewConstructor(
      datasets = datasets,
      functions = functions,
      variables = Some(variables),
      query = query,
      projectId = projectId,
      schema = ViewConstructor.buildBase(
                  query,
                  datasets.mapValues { id => 
                    () => Vizier.sparkSession.createDataFrame(new java.util.ArrayList[Row](), StructType(datasetSchemas(id)))
                  },
                  functions,
                  variables.mapValues { id => 
                    () => Literal(null, variableTypes(id))
                  }
                ).schema
    )
  }

  def buildBase(
    query: String, 
    tableMappings: Map[String, () => DataFrame],
    functions: Map[String, (Identifier, String, String)],
    variables: Map[String, () => Expression],
  ): DataFrame =
    InjectedSparkSQL(
      sqlText = query, 
      tableMappings = tableMappings,
      allowMappedTablesOnly = true,
      functionMappings = 
        functions.map { 
          case (name, (identifier, MIME.PYTHON, code)) => 
            name -> PythonProcess.udfBuilder(identifier, code)
          case (name, (_, mimeType, _)) => 
            throw new IllegalArgumentException(s"Unsupported user-defined-function $name with type ${mimeType}")
        }
        .toMap,
      variableReferences = 
        variables
    )
}
