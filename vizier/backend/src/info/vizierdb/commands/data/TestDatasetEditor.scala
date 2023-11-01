package info.vizierdb.commands.data

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.VizierException
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.SparkSchema
import org.apache.spark.sql.types.StructField
import java.io.FileNotFoundException
import java.io.IOException
import info.vizierdb.api.FormattedError
import info.vizierdb.filestore.Staging
import info.vizierdb.spark.LoadConstructor
import info.vizierdb.catalog.PublishedArtifact
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.spark.load.LoadSparkCSV
import spire.syntax.action
import info.vizierdb.spark.load.LoadSparkDataset
import info.vizierdb.spark.DataFrameConstructor
import info.vizierdb.util.ExperimentalOptions
import info.vizierdb.spark.SparkSchema.dataTypeFormat
import org.apache.spark.sql.types.DataType

object TestDatasetEditor
  extends Command
  with LazyLogging
{
    val PARAM_DATASET = "dataset"


    def name: String = "Test Dataset Editor"

    def parameters: Seq[Parameter] = Seq(
        DatasetParameter(id = PARAM_DATASET, name = "Dataset")
    )

    def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction = {
        return ProvenancePrediction.empty
    }

    def format(arguments: Arguments): String =  {
        return "Test Dataset Editor"
    }

    def process(arguments: Arguments, context: ExecutionContext): Unit = {
        return context.message("Test Dataset Editor")
    }

    def title(arguments: Arguments): String = 
        "Test Dataset Editor"

}
