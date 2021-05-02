package info.vizierdb.commands.transform

import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.types.ArtifactType
import info.vizierdb.catalog.Artifact

object FilterDataset 
  extends SQLTemplateCommand
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_FILTER = "filter"

  def name = "Filter Dataset"
  def templateParameters = Seq[Parameter](
    DatasetParameter(id = PARAM_DATASET, name = "Input Dataset"),
    StringParameter(id = PARAM_FILTER, name = "Filter")
  )

  def format(arguments: Arguments): String = 
    s"SELECT * FROM ${arguments.get[String](PARAM_DATASET)} WHERE ${arguments.get[String](PARAM_FILTER)}"

  def title(arguments: Arguments): String =
  {
    s"Filter ${arguments.get[String](PARAM_DATASET)}"
  }

  def query(arguments: Arguments, context: ExecutionContext): (Map[String, Artifact], String) =
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val dataset = context.artifact(datasetName) 
                         .getOrElse { throw new RuntimeException(s"Dataset $datasetName not found.")}
    if(dataset.t != ArtifactType.DATASET){
      throw new RuntimeException(s"$datasetName is not a dataset")
    }
    val query = s"SELECT * FROM __input__dataset__ WHERE ${arguments.get[String](PARAM_FILTER)}"
    val deps = Map("__input__dataset__" -> dataset)
    return (deps, query)
  }

  def predictProvenance(arguments: Arguments): Option[(Seq[String], Seq[String])] = 
    Some( (
      Seq(arguments.get[String](PARAM_DATASET)),
      Seq(arguments.getOpt[String](PARAM_OUTPUT_DATASET).getOrElse(DEFAULT_DS_NAME))
    ) )
}
