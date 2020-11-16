package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Artifact }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.artifacts.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import org.mimirdb.api.request.LoadInlineRequest

case class CreateDataset(
  projectId: Identifier,
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  name: Option[String],
  properties: Option[Map[String, JsValue]],
  annotations: Option[DatasetAnnotation]
)
  extends Request
{
  def handle: Response = 
  {
    DB.autoCommit { implicit s => 
      val project = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }

      val artifact = Artifact.make(
        project.id, 
        ArtifactType.DATASET,
        MIME.DATASET_VIEW,
        Array[Byte]()
      )

      LoadInlineRequest(
        schema = columns.map { _.toSpark },
        data = rows.map { _.values },
        dependencies = None,
        resultName = Some(artifact.nameInBackend),
        properties = properties,
        humanReadableName = name
      ).handle

      RawJsonResponse(
        artifact.summarize(name.getOrElse { null })
      )
    }
  } 
}

object CreateDataset
{
  implicit val format: Format[CreateDataset] = Json.format
}