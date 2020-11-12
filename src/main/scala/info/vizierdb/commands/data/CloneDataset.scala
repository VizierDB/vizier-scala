package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException

object CloneDataset extends Command
{
  def name: String = "Clone Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    StringParameter(id = "name", name = "Name of Copy")
  )
  def format(arguments: Arguments): String = 
    s"CLONE DATASET ${arguments.pretty("dataset")} TO ${arguments.pretty("name")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val artifact = context.artifact(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }

    context.output(arguments.get[String]("name"), artifact)
    context.message("Dataset Cloned")
  }
}