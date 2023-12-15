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
package info.vizierdb.commands.mimir

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import info.vizierdb.commands._
import info.vizierdb.commands.mimir.imputation._
import java.util.UUID
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.{ DataFrame, Column }
import java.io.File
import java.io.FileOutputStream
import scala.io.Source
import info.vizierdb.filestore.Filestore
import info.vizierdb.catalog.CatalogDB

case class ModelConfiguration(
  column: String,
  model: String,
  file: String
)

object ModelConfiguration
{
  implicit val format: Format[ModelConfiguration] = Json.format
}


object MissingValue
  extends LensCommand
{

  val PARAM_COLUMNS = "columns"
  val PARAM_MODEL = "model"
  val PARAM_SAVED_MODEL = "uuid"

  val PROP_MODELS = "models"
  val MODEL_SUMMARY_FILE = "summary.json"

  def name = "Impute Missing Values"

  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_COLUMNS, name = "Columns", components = Seq(
      TemplateParameters.COLUMN,
      EnumerableParameter(id = PARAM_MODEL, name = "Model", values = EnumerableValue.withNames(
        "Mean"                   -> "MeanMedianImputer/mean",
        "Median"                 -> "MeanMedianImputer/median",
        "Naive Bayes"            -> "MulticlassImputer/NaiveBayes",
        "Random Forest"          -> "MulticlassImputer/RandomForest",
        "Decision Tree"          -> "MulticlassImputer/DecisionTree",
        "Gradient Boosted Tree"  -> "MulticlassImputer/GradientBoostedTreeBinary",
        "Logistic Regression"    -> "MulticlassImputer/LogisticRegression",
        "One vs Rest"            -> "MulticlassImputer/OneVsRest",
        "Linear SVM"             -> "MulticlassImputer/LinearSupportVectorMachineBinary",
        "MultilayerPerceptron"   -> "MulticlassImputer/MultilayerPerceptron"
      ), default = Some(2)),
    )),
    CachedStateParameter(id = PARAM_SAVED_MODEL, name = "Model", required = false, hidden = true)
  )

  def format(arguments: Arguments): String = 
    s"IMPUTE MISSING VALUES ON ${arguments.getList("columns").map { "COLUMN "+_.get[Int]("column") }.mkString(", ")}"

  def title(arguments: Arguments): String =
    s"IMPUTE ${arguments.pretty(PARAM_DATASET)}"


  val DEFAULT_MODEL = "MulticlassImputer"
  val DEFAULT_STRATEGY = "NaiveBayes"

  val MODEL = "([a-zA-Z]+)/([a-zA-Z]+)".r

  def getImputer(col: String, name: String) =
    name match {
      case MODEL("MeanMedianImputer", strategy) => 
        MeanMedianImputer(col, strategy)
      case MODEL("MulticlassImputer", strategy) => 
        MulticlassImputer(col, strategy)
      case x => 
        throw new VizierException(s"Unknown imputation strategy: $x")
    }

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {
    /** column_name -> (model, strategy) **/
    var columns: Seq[(String, MissingValueImputer)] = 
      arguments.getList(PARAM_COLUMNS)
               .map { config =>
                  val col = df.schema(config.get[Int](TemplateParameters.PARAM_COLUMN)).name
                  
                  col -> getImputer( col, config.get[String](PARAM_MODEL) )
               }

    assert(columns.map { _._1 }.toSet.size == columns.size, "Column names must be unique")
    
    val modelArtifact:Artifact = 
         // Discard the old model if we're asked to reset it.
         // Otherwise, try to load the old model.
      arguments.getOpt[Identifier](PARAM_SAVED_MODEL)
               .map { id => 
                  CatalogDB.withDBReadOnly { implicit s => 
                    Artifact.get(id, Some(context.projectId))
                  }
               }
               .getOrElse { 
         // If we don't have/can't use the old model, create a new one
         // Don't go through context to create it so we don't pollute the namespace.
                 CatalogDB.withDB { implicit s => 
                   Artifact.make(
                     projectId = context.projectId,
                     t = ArtifactType.FILE,
                     data = Array[Byte](),
                     mimeType = MIME.RAW
                   )
                 }
               }

    val modelDir = modelArtifact.absoluteFile
    if(!modelDir.isDirectory){ modelDir.mkdir() }

    // Figure out what models we need
    val existingModels = modelArtifact.filePropertyOpt(PROP_MODELS)
                            .map { _.as[Seq[ModelConfiguration]] }
                            .getOrElse { Seq.empty }
    val existingModelLookup = existingModels.groupBy { _.column }

    val neededModels = 
      columns.filter { case (column, model) => 
        existingModelLookup.get(column) match {
          // Check if we have the right model
          case Some(columnModels) => 
            ! columnModels.exists { _.model == model.name }
          
          // If we don't have anything for the column, we need one
          case None => 
            true
        }
      }


    // Generate the necessary ones
    val newModels: Seq[ModelConfiguration] =
      neededModels.zipWithIndex.map { case ((column, model), idx) => {
        val modelConfig = 
          ModelConfiguration(
            column = column,
            model = model.name,
            file = 
              (idx+existingModels.size).toString + "-" +
              column.replaceAll("[^a-zA-Z0-9]+", "_") + "-" +
              model.name.replaceAll("[^a-zA-Z]+", "_")
          )

        val modelFile = new File(modelDir, modelConfig.file)

        // The model is responsible for serializing itself into the file
        model.model(df, modelFile)

        modelConfig
      }}

    val updatedModelSummary = Json.toJson(existingModels ++ newModels)

    val summaryFile = new File(modelDir, MODEL_SUMMARY_FILE)
    val summary = new FileOutputStream(summaryFile)
    summary.write(updatedModelSummary.toString.getBytes())
    summary.close()
    assert(summaryFile.exists())

    CatalogDB.withDB { implicit s => 
      modelArtifact.updateFileProperty(
        PROP_MODELS,
        Json.toJson(existingModels ++ newModels)
      )
    }

    Map(
      PARAM_SAVED_MODEL ->
        modelArtifact.id
    )
  }

  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {

    val modelDir =
      Filestore.getAbsolute(
        projectId,
        arguments.get[Identifier](PARAM_SAVED_MODEL)
      )

    val summary = 
        Source.fromFile(new File(modelDir, MODEL_SUMMARY_FILE))
              .mkString

    val models:Map[String,Seq[ModelConfiguration]] = 
      Json.parse(summary)
          .as[Seq[ModelConfiguration]]
          .groupBy { _.column }

    val output = 
      arguments.getList(PARAM_COLUMNS)
           .foldLeft(df) { (imputedDf, config) =>
              val col = df.schema(config.get[Int](TemplateParameters.PARAM_COLUMN)).name
              val model = getImputer( col, config.get[String](PARAM_MODEL) )
              val modelConfig = models.get(col)
                                      .getOrElse { 
                                        throw new RuntimeException(s"Missing models for $col from ${models.keys.mkString(", ")}")
                                      }
                                      .find { _.model == model.name }
                                      .getOrElse { 
                                        throw new RuntimeException(s"Missing models for ${model.name} from ${models(col).map { _.model }.mkString(", ")}")
                                      }

              model.impute(df, new File(modelDir, modelConfig.file))
            }
    return output
  }

}

