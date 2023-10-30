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

    def predictProvenance(arguments: Seq[CommandArgument])(implicit context: ExecutionContext, catalog: CatalogDB): ProvenancePrediction = {
        val dataset = arguments.find { _.id == PARAM_DATASET }.get.value.asInstanceOf[JsString].value
        ProvenancePrediction(
            artifact = Some(dataset),
            artifacts = Seq(dataset)
        )
    }

    def format(arguments: Seq[CommandArgument])(implicit context: ExecutionContext, catalog: CatalogDB): FormattedError = {
        val dataset = arguments.find { _.id == PARAM_DATASET }.get.value.asInstanceOf[JsString].value
        val schema = catalog.datasets.get(dataset).get.schema
        val columns = schema.fields.map { field => 
            Json.obj(
                "name" -> field.name,
                "type" -> dataTypeFormat.writes(field.dataType)
            )
        }
        FormattedError(
            "success",
            Json.obj(
                "columns" -> columns
            )
        )
    }

    def process(arguments: Seq[CommandArgument], context: ExecutionContext)(implicit catalog: CatalogDB): Unit = {
        val dataset = arguments.find { _.id == PARAM_DATASET }.get.value.asInstanceOf[JsString].value
        val schema = catalog.datasets.get(dataset).get.schema
        val columns = schema.fields.map { field => 
            Json.obj(
                "name" -> field.name,
                "type" -> dataTypeFormat.writes(field.dataType)
            )
        }
        val rows = catalog.datasets.get(dataset).get.rows
        val data = rows.map { row => 
            val values = row.values.zip(schema.fields).map { case (value, field) =>
                field.dataType match {
                    case DataType.fromJson("string") => JsString(value.asInstanceOf[String])
                    case DataType.fromJson("integer") => JsNumber(value.asInstanceOf[Int])
                    case DataType.fromJson("double") => JsNumber(value.asInstanceOf[Double])
                    case DataType.fromJson("boolean") => JsBoolean(value.asInstanceOf[Boolean])
                    case _ => JsNull
                }
            }
            JsArray(values)
        }
        val result = Json.obj(
            "columns" -> columns,
            "data" -> data
        )
        context.output("result", result)
    }

    def title(arguments: Arguments): String = 
        "Test Dataset Editor"

}
