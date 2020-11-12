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
import org.mimirdb.api.request.QueryMimirRequest
import org.mimirdb.api.request.CreateViewRequest

object EmptyDataset extends Command
{
  def name: String = "Empty Dataset"
  def parameters: Seq[Parameter] = Seq(
    StringParameter(id = "name", name = "Name of Dataset")
  )
  def format(arguments: Arguments): String = 
    s"CREATE EMPTY DATASET ${arguments.pretty("name")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val (dsName, dsId) = context.outputDataset(arguments.get[String]("name"))
    CreateViewRequest(
      input = Map.empty,
      functions = None,
      query = "SELECT '' AS unnamed_column",
      resultName = Some(dsName),
      properties = None
    ).handle
    context.message("Empty Dataset Created")
  }
}