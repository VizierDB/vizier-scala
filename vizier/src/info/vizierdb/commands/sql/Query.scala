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
package info.vizierdb.commands.sql

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.{ InjectedSparkSQL, ViewConstructor }
import info.vizierdb.catalog.ArtifactSummary
import info.vizierdb.Vizier
import org.apache.spark.sql.{ DataFrame, SparkSession, AnalysisException }

object Query extends Command
  with LazyLogging
{

  val TEMPORARY_DATASET = "temporary_dataset"

  def name: String = "SQL Query"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "sql", name = "SQL Code"),
    StringParameter(id = "output_dataset", name = "Output Dataset", required = false)
  )
  def format(arguments: Arguments): String = 
    s"${arguments.pretty("source")} TO ${arguments.pretty("output_dataset")}"
  def title(arguments: Arguments): String =
    arguments.getOpt[String]("output_dataset")
             .map { "SELECT into " + _ }
             .getOrElse { "SQL Query" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val scope = context.allDatasets
    val functions = context.scope
                           .toSeq
                           .filter { case (name, summary) => 
                                      summary.t == ArtifactType.FUNCTION }
                           .map { case (name, summary:ArtifactSummary) => name -> summary.id }
                           .toMap
    val datasetName = arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
    val query = arguments.get[String]("source")

    logger.debug(s"$scope : $query")

    try { 
      logger.trace("Creating view")
      val output = context.outputDataset(
        datasetName,
        ViewConstructor(
          datasets = scope.mapValues { _.id },
          functions = functions,
          query = query,
          projectId = context.projectId
        )
      )
      val df = DB.autoCommit { implicit s => output.dataframe }

      val (viewRefs, functionRefs) = 
        InjectedSparkSQL.getDependencies(query)

      logger.trace("View created; Gathering dependencies")
      for(dep <- viewRefs){
        context.inputs.put(dep, scope(dep).id)
      }
      for(dep <- functionRefs){
        context.inputs.put(dep, functions(dep))
      }

      logger.trace("Rendering dataset summary")
      context.displayDataset(datasetName)
    } catch { 
      case e: info.vizierdb.api.FormattedError => 
        context.error(e.getMessage)
      case e:AnalysisException => {
        e.printStackTrace()
        context.error(prettyAnalysisError(e, query))
      }
    }
  }

  def computeDependencies(sql: String): Seq[String] =
  {
    val (views, functions) = InjectedSparkSQL.getDependencies(sql)
    return views.toSeq
  }

  def predictProvenance(arguments: Arguments) =
    Some( (
      computeDependencies(arguments.get[String]("source")),
      Seq(
        arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
      )
    ) )
  def prettyAnalysisError(e: AnalysisException, query: String): String =
    prettySQLError(e.message, query, e.line, e.startPosition.getOrElse(0))

  def prettySQLError(
    message: String, 
    query: String, 
    targetLine: Option[Int] = None, 
    startPosition: Int = 0
  ): String = {
    val sb = new StringBuilder(message+"\n")
    sb.append("in\n")
    
    def normalLine(l: String)    { sb.append(s"    $l\n") }
    def highlightLine(l: String) { sb.append(s">>> $l\n") }
    
    val queryLines = query.split("\n")
    
    targetLine match { 
      case None => queryLines.foreach { normalLine(_) }
      case Some(lineNo) => {
        // The line number we get is 1-based.  query's lines are 0-based.
        if(lineNo > queryLines.size){ 
          sb.append(s"Query Trace Error: Got Line #$lineNo out of ${queryLines.size}")
          queryLines.foreach { normalLine(_) }
        } else {
          if(lineNo > 2){
            normalLine(queryLines(lineNo - 3))
          }
          if(lineNo > 1){
            normalLine(queryLines(lineNo - 2))
          }
          highlightLine(queryLines(lineNo - 1))
          sb.append("    ")
          (0 until (startPosition-2)).foreach { sb.append(' ') }
          sb.append("^\n")

          if(queryLines.size > lineNo + 0){
            normalLine(queryLines(lineNo))
          }
          if(queryLines.size > lineNo + 1){
            normalLine(queryLines(lineNo+1))
          }
        }
      }
    }

    sb.toString()
  }
}

