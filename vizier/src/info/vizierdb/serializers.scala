package info.vizierdb

import play.api.libs.json._
import info.vizierdb.api.AppendModule

object serializers
{

  implicit val propertyFormat: Format[serialized.Property] = Json.format
  implicit val propertyListFormat: Format[serialized.PropertyList.T] = Json.format

  implicit val branchSourceFormat: Format[serialized.BranchSource] = Json.format

  implicit val datasetColumnFormat: Format[serialized.DatasetColumn] = Json.format
  implicit val datasetRowFormat: Format[serialized.DatasetRow] = Json.format
  implicit val datasetAnnotationFormat: Format[serialized.DatasetAnnotation] = Json.format

}