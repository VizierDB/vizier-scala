/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
package info.vizierdb.commands

import java.io.File
import play.api.libs.json._
import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.Vizier
import info.vizierdb.catalog.{ Artifact, Module, Cell }
import org.mimirdb.api.request.QueryTableRequest
import info.vizierdb.VizierException
import info.vizierdb.catalog.binders._
import info.vizierdb.artifacts.Chart
import info.vizierdb.VizierAPI
import org.mimirdb.api.MimirAPI
import info.vizierdb.catalog.DatasetMessage
import info.vizierdb.catalog.ArtifactSummary
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField

class ExecutionContext(
  val projectId: Identifier,
  val scope: Map[String, ArtifactSummary],
  cell: Cell,
  module: Module
)
  extends LazyLogging
{
  val inputs = scala.collection.mutable.Map[String, Identifier]()
  val outputs = scala.collection.mutable.Map[String, Option[Artifact]]()
  val messages = scala.collection.mutable.Buffer[(String, Array[Byte])]()
  var errorMessages = scala.collection.mutable.Buffer[(String, Array[Byte])]()

  /**
   * Check to see if the specified artifact appears in the scope
   */
  def artifactExists(name: String): Boolean = {
    scope.contains(name.toLowerCase()) || outputs.contains(name.toLowerCase())
  }

  /**
   * Retrieve the specified artifact
   *
   * @param   name            The user-facing name of the artifact
   * @returns                 The Artifact object assoicated with this name
   */
  def artifact(name: String, registerInput: Boolean = true): Option[Artifact] = 
  {
    logger.debug(s"Retrieving $name")
    if(outputs contains name.toLowerCase()){
      val ret = outputs(name.toLowerCase())
      if(ret.isEmpty){ 
        throw new VizierException(s"$name was already deleted.")
      }
      return Some(ret.get)
    }
    val ret = scope.get(name.toLowerCase()).map { id =>
      DB readOnly { implicit s => id.materialize }
    }
    if(registerInput){ ret.foreach { a => inputs.put(name.toLowerCase(), a.id) } }
    return ret
  }

  /**
   * Retrieve the specified dataset
   *
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The backend name corresponding to the specified dataset
   */
  def dataset(name: String, registerInput: Boolean = true): Option[DatasetIdentifier] = 
    artifact(name, registerInput)
      .map { a => if(a.t != ArtifactType.DATASET) { 
                    throw new VizierException(s"$name is not a dataset (it's actually a ${a.t})" )
                  } else { a.nameInBackend } }

  /** 
   * Retrieve the spark dataframe for the specified dataset
   * 
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The spark dataframe for the specified datset
   */
  def dataframe(name: String, registerInput: Boolean = true): DataFrame =
    dataframeOpt(name, registerInput).getOrElse {
      throw new VizierException(s"No such dataset: $name")
    }

  /** 
   * Retrieve the spark dataframe for the specified dataset
   * 
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The spark dataframe for the specified datset
   */
  def dataframeOpt(name: String, registerInput: Boolean = true): Option[DataFrame] =
    dataset(name, registerInput).map { MimirAPI.catalog.get(_) }

  /** 
   * Retrieve the schema for the specified dataset
   * 
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The spark dataframe for the specified datset
   */
  def datasetSchema(name: String, registerInput: Boolean = true): Option[Seq[StructField]] =
    dataframeOpt(name, registerInput).map { _.schema.fields } 

  /**
   * Retrieve all datasets in scope
   */
  def allDatasets: Map[String, Artifact] =
  {
    DB.readOnly { implicit s => 
      (
        scope.filter { _._2.t == ArtifactType.DATASET }
             .filterNot { outputs contains _._1 }
             .mapValues { _.materialize } ++
        outputs.filter { _._2.isDefined }
               .mapValues { _.get }
               .filter { _._2.t == ArtifactType.DATASET }
      // The following line forces execution of the query
      // HERE, instead of "intelligently" deferring query
      // execution until we're outside of the "readOnly"
      // block
      ).toIndexedSeq.toMap
    }
  }

  /**
   * Allocate, output and optionally message a chart
   *
   * @param   chart           The chart description
   * @param   withMessage     Include a message containing the chart
   * @param   withArtifact    Include an message containing the chart
   */
  def chart(chart: Chart, withMessage: Boolean = true, withArtifact: Boolean = true): Boolean =
  {
    val dataset = artifact(chart.dataset)
                             .getOrElse{ 
                               error(s"Dataset ${chart.dataset} does not exist")
                               return false
                             }
    val df = 
      MimirAPI.catalog.getOption(dataset.nameInBackend)
                      .getOrElse {
                        error(s"Dataset ${chart.dataset} [id:${dataset.nameInBackend}] does not exist")
                        return false
                      }
    val encoded = 
      chart.render(df).toString.getBytes

    if(withMessage){
      message(
        mimeType = MIME.CHART_VIEW, 
        content = encoded
      )
    }
    if(withArtifact){
      output(
        name = chart.name,
        t = ArtifactType.CHART,
        data = encoded,
        mimeType = MIME.CHART_VIEW
      )
    }

    return true
  }

  /**
   * Allocate and output an artifact
   *
   * @param   name            The user-facing name of the artifact
   * @param   t               The type of the artifact
   * @param   data            The content of the dataset
   * @returns                 The newly allocated Artifact object
   */
  def output(name: String, t: ArtifactType.T, data: Array[Byte], mimeType: String = "text/plain"): Artifact =
  { 
    val artifact = DB autoCommit { implicit s => Artifact.make(projectId, t, mimeType, data) }
    outputs.put(name.toLowerCase(), Some(artifact))
    return artifact
  }

  /**
   * Output an existing artifact (possibly under a new name)
   *
   * @param    name           The new name of the artifact
   * @param    artifact       The artifact to output
   */
  def output(name: String, artifact: Artifact): Artifact =
  {
    outputs.put(name.toLowerCase(), Some(artifact))
    return artifact
  }

  /**
   * Delete an artifact from the scope
   *
   * @param     name          The artifact to delete
   */
  def delete(name: String) =
  {
    outputs.put(name.toLowerCase(), None)
  }

  /**
   * Allocate a new dataset object and register it as an output
   * 
   * @param   name            The user-facing name of the dataset
   * @return                  The newly allocated backend-facing name and its identifier
   */
  def outputDataset(name: String): (String, Identifier) =
    { val ds = output(name, ArtifactType.DATASET, Array[Byte](), MIME.DATASET_VIEW); (ds.nameInBackend, ds.id) }

  /**
   * Allocate a new dataset object and register it as an output
   * 
   * @param   name            The user-facing name of the dataset
   * @returns                 The newly allocated backend-facing name
   */
  def outputFile(name: String, mimeType: String = MIME.TEXT, properties: JsObject = Json.obj()): Artifact =
    output(name, ArtifactType.FILE, properties.toString.getBytes, mimeType)

  /**
   * Record that this execution failed with the specified name
   * 
   * @param   message         The error message to communicate to the user
   */
  def error(message: String)
  {
    errorMessages.append( (MIME.TEXT, message.getBytes) )
  }

  /**
   * Communicate a message to the end-user.
   * 
   * @param   content         The text message to communicate to the user
   */
  def message(content: String)
  {
    message(MIME.TEXT, content.getBytes())
  }

  /**
   * Communicate a message to the end-user.
   *
   * @param   mimeType        The MIME-Type of the message content
   * @param   content         The text to communicate
   */
  def message(mimeType: String, content: String)
  {
    message(mimeType, content.getBytes())
  }

  /**
   * Communicate a message to the end-user.
   *
   * @param   mimeType        The MIME-Type of the message content
   * @param   content         The bytes of data to communicate
   */
  def message(mimeType: String, content: Array[Byte])
  {
    logger.trace(s"APPEND[$mimeType]: $content")
    messages.append( (mimeType, content) )
  }

  /**
   * Communicate a dataset to the end user.
   * 
   * @param   artifactId      The artifact identifier of the dataset to display 
   */
  def displayDataset(name: String, offset: Long = 0l, limit: Int = VizierAPI.DEFAULT_DISPLAY_ROWS) = 
  {
    val dataset = artifact(name).get
    val data =  dataset.getDataset(
                  offset = Some(offset),
                  limit  = Some(limit),
                  includeUncertainty = true
                )
    val rowCount: Long = 
        data.properties
            .get("count")
            .map { _.as[Long] }
            .getOrElse { MimirAPI.catalog
                                 .get(Artifact.nameInBackend(
                                          ArtifactType.DATASET, 
                                          dataset.id
                                      ))
                                 .count() }

    message(MIME.DATASET_VIEW, 
      Json.toJson(
        DatasetMessage(
          name = Some(name),
          artifactId = dataset.id,
          projectId = dataset.projectId,
          offset = offset,
          dataCache = Some(data),
          rowCount = rowCount,
          created = dataset.created
        )
      ).toString.getBytes
    )
  }

  /**
   * Modify the arguments of the calling cell (DANGEROUS)
   *
   * Alter a subset of the arguments to the cell currently being
   * executed.  This function should be used with EXTREME care.
   *
   * The motivating use case for this function is when the cell
   * is capable of making educated, but non-static guesses about 
   * the "correct" value about one or more of its parameters, while
   * also providing the ability for the user to later re-run the
   * cell, overriding some of these guesses.  For example, the Load 
   * Dataset cell attempts to infer the schema of the loaded dataset, 
   * but should also allow users to manually override the guessed
   * schema.
   * 
   * Broadly, the guideline for using this function is that the
   * act of overriding the cell's arguments MUST be idempotent.  That
   * is, if the cell is re-run, the output should be identical.
   *
   * The other viable use case is when the cell's behavior is 
   * nondeterministic (e.g., a Sample cell).  A "seed" parameter can
   * be registered as an argument to make subsequent calls 
   * deterministic.
   */
  def updateArguments(args: (String, Any)*)
  {
    val command = Commands.get(module.packageId, module.commandId)
    val newArgs = command.encodeArguments(args.toMap, module.arguments.value.toMap)
    DB.autoCommit { implicit s => 
      cell.replaceArguments(newArgs)
    }
  }

  /**
   * Modify the arguments of the calling cell (DANGEROUS)
   *
   * Alter a subset of the arguments to the cell currently being
   * executed.  This function should be used with EXTREME care.
   *
   * The motivating use case for this function is when the cell
   * is capable of making educated, but non-static guesses about 
   * the "correct" value about one or more of its parameters, while
   * also providing the ability for the user to later re-run the
   * cell, overriding some of these guesses.  For example, the Load 
   * Dataset cell attempts to infer the schema of the loaded dataset, 
   * but should also allow users to manually override the guessed
   * schema.
   * 
   * Broadly, the guideline for using this function is that the
   * act of overriding the cell's arguments MUST be idempotent.  That
   * is, if the cell is re-run, the output should be identical.
   *
   * The other viable use case is when the cell's behavior is 
   * nondeterministic (e.g., a Sample cell).  A "seed" parameter can
   * be registered as an argument to make subsequent calls 
   * deterministic.
   */
  def updateJsonArguments(args: (String, JsValue)*)
  {
    val newArgs = JsObject(module.arguments.value ++ args.toMap)
    DB.autoCommit { implicit s => 
      cell.replaceArguments(newArgs)
    }
  }

  override def toString: String =
    {
      s"SCOPE: { ${scope.map { case (ds, art) => ds+" -> "+art.id }.mkString(", ")} }"
    }

  def isError = !errorMessages.isEmpty
}

