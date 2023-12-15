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
import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.{ InjectedSparkSQL, ViewConstructor }
import info.vizierdb.Vizier
import org.apache.spark.sql.{ DataFrame, SparkSession, AnalysisException }
import info.vizierdb.catalog.Artifact
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.CatalogDB
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.StructField

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
    arguments.pretty("source")
  def title(arguments: Arguments): String =
    arguments.getOpt[String]("source")
             .flatMap { source =>
                // If there's a -- comment on the first line, use it as the
                // title.
               if(source.startsWith("--")){
                Some(
                  source.split("\n").head  // only take the first line.
                        .drop(2)           // drop the '--'
                        .trim()            // drop any leading/tailing whitespace
                )
               } else { None }
             }
             .orElse { 
                arguments.getOpt[String]("output_dataset")
                         .map { "SELECT into " + _ }
             }
    
             .getOrElse { "SQL Query" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    logger.trace(s"Available artifacts: \n${context.scope.map { case (name, summary) => s"$name -> ${summary.t}" }.mkString("\n")}")
    val datasets = context.allDatasets
    val functions = context.scope
                           .toSeq
                           .filter { _._2.t == ArtifactType.FUNCTION }
                           .toMap
                           .mapValues { _.id }
    val parameters = context.scope
                           .toSeq
                           .filter { _._2.t == ArtifactType.PARAMETER }
                           .toMap
    val datasetName = arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
    val query = arguments.get[String]("source")


    try { 
      val parsed = InjectedSparkSQL.parse(sqlText = query)
      
      logger.trace(s"Creating view for \n$query\nAvailable functions: ${functions.map { _._1 }.mkString(", ")}")
      val fnDeps: Map[String, (Identifier, String, String)] = 
        InjectedSparkSQL.getFunctionReferences(parsed)
                        .toSeq
                        .collect { case f if functions contains f => 
                                      f -> 
                                      CatalogDB.withDB { implicit s => 
                                        val id = functions(f)
                                        val a = Artifact.get(id, Some(context.projectId))
                                        (
                                          a.id,
                                          a.mimeType,
                                          a.string
                                        )
                                      }
                                  }
                        .toIndexedSeq // This is needed to force materialization
                        .toMap

      logger.trace(s"${fnDeps.keys.size} function dependencies: ${fnDeps.keys.mkString(", ")}")

      val datasetIds: Map[Identifier, Artifact] = 
        context.allDatasets.map { d => d._2.id -> d._2 }.toMap
      val parameterIds: Map[Identifier, Artifact] = 
        parameters.map { d => d._2.id -> d._2 }.toMap

      val view = 
        try {
          ViewConstructor(
            datasets = datasets.mapValues { _.id }: Map[String, Identifier],
            functions = fnDeps: Map[String, (Identifier, String, String)],
            variables = parameters.mapValues { _.id }: Map[String, Identifier],
            query = query: String,
            projectId = context.projectId: Identifier,
            datasetSchemas = { id:Identifier => datasetIds(id).datasetSchema }: Identifier => Seq[StructField],
            variableTypes = { id:Identifier => parameterIds(id).parameter.dataType:DataType }: Identifier => DataType,
          )
        } catch {
          case _:InjectedSparkSQL.NotAQueryException =>
            context.error("Only queries are supported in SQL cells")
            return
        }

      val output = context.outputDataset(datasetName, view)
      val df = CatalogDB.withDB { implicit s => output.dataframe }
      // df.explain()

      logger.trace("View created; Gathering dependencies")
      for(dep <- view.viewDeps)
      {
        context.inputs.put(dep, datasets(dep).id)
      }
      for(dep <- view.fnDeps)
      {
        // view.functions includes all functions referenced by the query,
        // including those supplied by spark/plugins, etc...  We only
        // want to register dependencies on functions explicitly in the
        // context's scope.
        if(functions contains dep){
          context.inputs.put(dep, functions(dep))
        }
      }
      for(dep <- view.varDeps)
      {
        context.inputs.put(dep, parameters(dep).id)
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
    val (views, functions, variables) = InjectedSparkSQL.getDependencies(sql)

            // Include all views
    return views.toSeq++
            // Include only non-built in functions
            functions.toSeq.filterNot { Vizier.sparkSession.catalog.functionExists(_) } ++
            // Include all $-referenced variables 
            variables
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) =
    try {
      ProvenancePrediction
        .definitelyReads(
          computeDependencies(arguments.get[String]("source")):_*
        )
        .definitelyWrites(
          arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
        )
        .andNothingElse
    } catch {
      case t:Throwable => 
        ProvenancePrediction.default
    }
  
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

