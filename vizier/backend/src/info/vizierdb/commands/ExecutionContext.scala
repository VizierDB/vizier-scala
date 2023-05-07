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
package info.vizierdb.commands

import java.io.File
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.Vizier
import info.vizierdb.catalog.{ Artifact, Workflow, Module, Cell, Result }
import info.vizierdb.VizierException
import info.vizierdb.catalog.binders._
import info.vizierdb.vega.{ Chart => VegaLiteChart }
import info.vizierdb.catalog.DatasetMessage
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.{ StructField, DataType }
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.delta.DeltaBus
import info.vizierdb.spark.DataFrameConstructor
import info.vizierdb.artifacts.Dataset
import info.vizierdb.viztrails.ScopeSummary
import info.vizierdb.catalog.ArtifactRef
import info.vizierdb.catalog.JavascriptMessage
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import org.apache.spark.ml.PipelineStage
import org.apache.spark.ml.Pipeline
import info.vizierdb.spark.PipelineModelConstructor
import info.vizierdb.spark.LoadConstructor
import org.apache.spark.ml.PipelineModel
import scala.io.Source
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.akka.VizierServer
import info.vizierdb.artifacts.VegaChart

class ExecutionContext(
  val projectId: Identifier,
  val scope: Map[String, Artifact],
  workflow: Workflow,
  cell: Cell,
  module: Module,
  stdout: (String, Array[Byte]) => Unit,
  stderr: String => Unit
)
  extends LazyLogging
{
  val inputs = scala.collection.mutable.Map[String, Identifier]()
  val outputs = scala.collection.mutable.Map[String, Option[Artifact]]()
  // val messages = scala.collection.mutable.Buffer[(String, Array[Byte])]()
  // var errorMessages = scala.collection.mutable.Buffer[(String, Array[Byte])]()
  var isError = false

  /**
   * Get a summary of the input scope
   */
  def inputScopeSummary: ScopeSummary = 
    ScopeSummary.withIds(scope.mapValues { _.id })

  /**
   * Get a summary of the input scope
   */
  def outputScopeSummary: ScopeSummary = 
    inputScopeSummary.copyWithOutputs(outputs.mapValues { _.map { _.id }}.toMap)

  /**
   * Check to see if the specified artifact appears in the scope
   */
  def artifactExists(name: String): Boolean = 
    scope.contains(name.toLowerCase()) || outputs.contains(name.toLowerCase())

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
    val ret = scope.get(name.toLowerCase())
    if(registerInput){ ret.foreach { a => inputs.put(name.toLowerCase(), a.id) } }
    return ret
  }

  /**
   * Retrieve the identifier of the specified artifact
   *
   * @param   name            The user-facing name of the artifact
   * @returns                 The Identifier object for the artifact associated with this name
   */
  def artifactId(name: String, registerInput: Boolean = true): Option[Identifier] = 
  {
    logger.debug(s"Retrieving $name id")
    if(outputs contains name.toLowerCase()){
      val ret = outputs(name.toLowerCase())
      return Some(ret.getOrElse {
        throw new VizierException(s"$name was already deleted.")
      }.id)
    }
    val ret = scope.get(name.toLowerCase()).map { _.id }
    if(registerInput){ ret.foreach { a => inputs.put(name.toLowerCase(), a) } }
    return ret
  }

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
    artifact(name, registerInput)
      .map { a => CatalogDB.withDBReadOnly { implicit s => a.dataframe }() }


  /**
   * Define and fit SparkML Pipeline
   */
  def createPipeline(
    input: String, 
    output: String = null,
    properties: Map[String,JsObject] = Map.empty
  )(stages: PipelineStage*): (Artifact, PipelineModel) =
    outputPipeline(
      input = input, 
      output = output,
      properties = properties,
      pipeline = new Pipeline().setStages(stages.toArray)
    )

  def outputPipeline(
    input: String, 
    pipeline: Pipeline, 
    output: String = null,
    properties: Map[String,JsObject] = Map.empty
  ): (Artifact, PipelineModel) =
  {
    val inputArtifact = artifact(input).getOrElse { 
                          throw new VizierException(s"Dataset $input does not exist")
                        }

    val inputDataframe = 
      CatalogDB.withDB { implicit s => inputArtifact.dataframe }()

    // Release the database lock while fitting
    logger.debug("Fitting pipeline")
    val model = pipeline.fit(inputDataframe)

    val outputArtifact = outputDatasetWithFile(
        Option(output).getOrElse(input), 
        { artifact => 
          logger.debug(s"Saving pipeline model to ${artifact.absoluteFile}")
          // Output the model
          model.save(artifact.absoluteFile.toString)

          (
            new PipelineModelConstructor(
              input = inputArtifact.id,
              url = FileArgument(fileid = Some(artifact.id)),
              projectId = projectId,
              schema = model.transform(inputDataframe).schema
            ), 
          )
        },
        properties
    )

    return (outputArtifact, model)
  }

  def outputDataframe(name: String, dataframe: DataFrame, properties: Map[String,JsObject] = Map.empty): Artifact =
  {
    outputDatasetWithFile(name, { artifact =>
      dataframe.write
               .parquet(artifact.absoluteFile.toString)
      (
        new LoadConstructor(
          url = FileArgument(fileid = Some(artifact.id)),
          format = "parquet",
          sparkOptions = Map(),
          contextText = Some(name),
          proposedSchema = Some(dataframe.schema),
          projectId = projectId
        ),
      )
    }, properties)
  }

  /** 
   * Retrieve the schema for the specified dataset
   * 
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The spark dataframe for the specified datset
   */
  def datasetSchema(name: String, registerInput: Boolean = true): Option[Seq[StructField]] =
    artifact(name, registerInput)
      .map { a => CatalogDB.withDB { implicit s => a.datasetSchema }}

  /**
   * Retrieve all datasets in scope
   */
  def allDatasets: Map[String, Artifact] =
  {
    CatalogDB.withDBReadOnly { implicit s => 
      (
        scope.filter { _._2.t == ArtifactType.DATASET }
             .filterNot { outputs contains _._1 } ++
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
   * Get a parameter artifact defined in an earlier cell
   * 
   * @param   name         The name of the artifact
   *
   * Parameters should be valid spark data values of the type provided.
   */
  def parameterOpt[T](name: String): Option[T] =
  {
    artifact(name)
      .filter { _.t == ArtifactType.PARAMETER }
      .map { _.parameter.nativeValue.asInstanceOf[T] }
  }

  /**
   * Get a parameter artifact defined in an earlier cell
   * 
   * @param   name         The name of the artifact
   *
   * Parameters should be valid spark data values of the type provided.
   */
  def parameter[T](name: String): T =
    parameterOpt[T](name).get

  /**
   * Set a parameter artifact for use in later cells
   * 
   * @param   name         The name of the artifact
   * @param   value        The value of the artifact
   * @param   dataType     The data type of the artifact
   * 
   * Parameters should be valid spark data values of the type provided.
   */
  def setParameter(name: String, value: Any, dataType: DataType)
  {
    output(
      name, 
      t = ArtifactType.PARAMETER,
      data = Json.toJson(serialized.ParameterArtifact.fromNative(value, dataType)).toString.getBytes,
      mimeType = MIME.JSON
    )
  }

  /**
   * Allocate, output and optionally message a Vega-Lite chart
   *
   * @param   chart           The chart description
   * @param   identifier      An artifact identifier for the chart (or omit for no artifact)
   * @param   withMessage     set to true to emit a message containing the chart
   */
  def vegalite(chart: VegaLiteChart[_], identifier: String = null, withMessage: Boolean = true): Boolean =
  {
    val encoded = chart.export
    if(withMessage){
      message(
        mimeType = MessageType.VEGALITE.toString, 
        content = encoded.toString,
      )
    }
    Option(identifier) match {
      case None => ()
      case Some(identifier) => 
        output(
          name = identifier,
          t = ArtifactType.VEGALITE,
          data = encoded.toString.getBytes,
          mimeType = MIME.JSON
        )
    }

    return true
  }

  /**
   * Allocate, output, and optionally message a Vega chart
   * 
   * @param   chart          The chart description
   * @param   identifier      An artifact identifier for the chart (or omit for no artifact)
   * @param   withMessage     set to true to emit a message containing the chart
   */
  def vega(chart: VegaChart, identifier: String = null, withMessage: Boolean = true): Boolean =
  {
    val encoded = Json.toJson(chart).toString.getBytes
    if(withMessage){
      message(
        mimeType = MessageType.VEGA.toString, 
        content = encoded,
      )
    }
    Option(identifier) match {
      case None => ()
      case Some(identifier) => 
        output(
          name = identifier,
          t = ArtifactType.VEGA,
          data = encoded,
          mimeType = MIME.JSON
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
    val artifact = CatalogDB.withDB { implicit s => Artifact.make(projectId, t, mimeType, data) }
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
  def outputDataset[T <: DataFrameConstructor](
    name: String, 
    constructor: T,
    properties: Map[String, JsValue] = Map.empty
  )(implicit writes: Writes[T]): Artifact =
    output(
      name, 
      ArtifactType.DATASET,
      Json.toJson(Dataset(
        constructor,
        properties,
      )).toString.getBytes,
      mimeType = MIME.RAW
    )

  def outputDatasetWithFile[T <: DataFrameConstructor](
    name: String,
    gen: Artifact => T,
    properties: Map[String, JsValue] = Map.empty
  )(implicit writes: Writes[T]): Artifact =
  {
    val artifact = CatalogDB.withDB { implicit s =>
      Artifact.make(
        projectId,
        ArtifactType.DATASET,
        MIME.RAW,
        Array[Byte]()
      )
    }
    val constructor = gen(artifact)
    val ds = Dataset(
              constructor,
              properties
             )
    output(
      name,
      CatalogDB.withDB { implicit s =>
        artifact.replaceData(
          Json.toJson(ds).toString.getBytes
        )
      }
    )
  }

  /**
   * Allocate a new file artifact and register it as an output
   * 
   * @param   name            The user-facing name of the file
   * @param   mimeType        The MIME type of the file
   * @param   properties      A key/value dictionary of properties for the file
   * @returns                 The newly allocated backend-facing name
   */
  def outputFilePlaceholder(name: String, mimeType: String = MIME.TEXT, properties: JsObject = Json.obj()): Artifact =
    output(name, ArtifactType.FILE, properties.toString.getBytes, mimeType)

  /**
   * Allocate a new dataset object and register it as an output
   * 
   * Allocate a new file artifact and register it as an output
   * 
   * @param   name            The user-facing name of the file
   * @param   mimeType        The MIME type of the file
   * @param   properties      A key/value dictionary of properties for the file
   * @param   genFile         A block that generates the file
   * @returns                 The newly allocated backend-facing name
   */
  def outputFile(name: String, mimeType: String = MIME.TEXT, properties: JsObject = Json.obj())
                (genFile: OutputStream => Unit): Artifact =
  {
    val placeholder = outputFilePlaceholder(name, mimeType, properties)
    val fout = new FileOutputStream(placeholder.absoluteFile)
    try {
      genFile(fout)
      fout.flush()
    } finally {
      fout.close()
    }
    return placeholder
  }

  /**
   * Retrieve a file artifact
   */
  def file[T](name: String)(readFile: Source => T): T =
  {
    val fileArtifact = artifact(name).getOrElse { throw new VizierException(s"File $name does not exist")}
    val data = fileArtifact.t match {
                  case ArtifactType.FILE => Source.fromFile(fileArtifact.absoluteFile)
                  case ArtifactType.BLOB => Source.fromBytes(fileArtifact.data)
               }
    val ret = readFile(data)
    data.close()
    return ret
  }

  /**
   * Record that this execution failed with the specified name
   * 
   * @param   message         The error message to communicate to the user
   */
  def error(message: String)
  {
    stderr(message)
    isError = true
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
    stdout(mimeType, content)
  }

  /**
   * Communicate a dataset to the end user.
   * 
   * @param   name      The name of the dataset to display 
   * @param   offset    (optional) The position to start the display at
   * @param   limit     (optional) The maximum number of rows to show
   */
  def displayDataset(name: String, offset: Long = 0l, limit: Int = VizierServer.DEFAULT_DISPLAY_ROWS) = 
  {
    val dataset = artifact(name).get

    val data =  CatalogDB.withDBReadOnly { implicit s => 
                  dataset.datasetData(
                    offset = Some(offset),
                    limit  = Some(limit),
                    includeCaveats = true
                  )
                }()
    val rowCount: Long = 
        CatalogDB.withDB { implicit s => 
          dataset.datasetProperty("count")
        }.map { _.as[Long] }
         .getOrElse {
            val df = CatalogDB.withDB { implicit s => dataset.dataframe }()
            val count = df.count()
            CatalogDB.withDB { implicit s => dataset.updateDatasetProperty("count", JsNumber(count)) }
            count
          }

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

  def dataset(name: String): Option[Dataset] = 
    artifact(name).map { a => 
      CatalogDB.withDB { implicit s => 
        a.datasetDescriptor
      }
    }

  def pipeline(name: String): Option[PipelineModel] =
    dataset(name).flatMap { 
      _.constructor match {
        case p:PipelineModelConstructor => Some(p.pipeline)
        case _ => None
      }
    }

  /**
   * Display HTML, along with embedded javascript
   * 
   * @param html          The HTML to display
   * @param javascript    Javascript code to execute after the HTML is loaded
   * @param dependencies  A list of Javascript files to load into the global
   *                      context.
   * 
   * Dependencies are typically cached by the client and only loaded once per
   * session.  
   */
  def displayHTML(
    html: String, 
    javascript: String = "", 
    javascriptDependencies: Iterable[String] = Seq.empty,
    cssDependencies: Iterable[String] = Seq.empty
  )
  {
    if(javascript.isEmpty && javascriptDependencies.isEmpty && cssDependencies.isEmpty){
      message(MIME.HTML, html.getBytes)
    } else {
      message(MIME.JAVASCRIPT, 
        Json.toJson(JavascriptMessage(
          html = html,
          code = javascript,
          js_deps = javascriptDependencies.toSeq,
          css_deps = cssDependencies.toSeq
        )).toString.getBytes()
      )
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
  def updateArguments(args: (String, Any)*): Arguments =
  {
    val command = Commands.get(module.packageId, module.commandId)
    val newArgs = command.encodeArguments(args.toMap, module.arguments.value.toMap)
    CatalogDB.withDB { implicit s => 
      val (newCell, newModule) = cell.replaceArguments(newArgs)
      DeltaBus.notifyUpdateCellArguments(workflow, newCell, newModule)
    }
    return Arguments(newArgs, command.parameters)
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
    CatalogDB.withDB { implicit s => 
      val (newCell, newModule) = cell.replaceArguments(newArgs)
      DeltaBus.notifyUpdateCellArguments(workflow, newCell, newModule)
    }
  }

  /**
   * Get a unique string for this execution
   */
  def executionIdentifier: String =
  {
    s"${module.id}_${cell.position}${cell.resultId.map { "_"+_ }.getOrElse("")}"
  }

  override def toString: String =
    {
      s"SCOPE: { ${scope.map { case (ds, art) => ds+" -> "+art.id }.mkString(", ")} }"
    }

  def spark = Vizier.sparkSession
}

