package info.vizierdb.ui.components.dataset

import info.vizierdb.types.Identifier
import info.vizierdb.ui.Vizier

sealed trait DatasetEditingDetails
{
  def url(datasetId: Identifier, projectId:Identifier): String
  def savedAsModule(newModuleId: Identifier): DatasetEditingDetails
}

case object DatasetNotEditable extends DatasetEditingDetails
{
  def url(datasetId: Identifier, projectId:Identifier): String =
    Vizier.links.spreadsheet(
      projectId = projectId, 
      datasetId = datasetId
    )
  def savedAsModule(newModuleId: Identifier): DatasetEditingDetails =
    throw new Exception("Can not save a non-editable dataset")
}

case class DatasetEditsAfterModule(branchId: Identifier, moduleId: Identifier) extends DatasetEditingDetails
{
  def url(datasetId: Identifier, projectId:Identifier): String =
    Vizier.links.spreadsheetAppend(
      projectId = projectId, 
      datasetId = datasetId, 
      branchId = branchId, 
      moduleId = moduleId
    )
  def savedAsModule(newModuleId: Identifier): DatasetEditingDetails =
  {
    DatasetEditsReplaceModule(
      branchId = branchId,
      moduleId = newModuleId
    )
  }
}

case class DatasetEditsReplaceModule(branchId: Identifier, moduleId: Identifier) extends DatasetEditingDetails
{
  def url(datasetId: Identifier, projectId:Identifier): String =
    Vizier.links.spreadsheetReplace(
      projectId = projectId, 
      datasetId = datasetId, 
      branchId = branchId, 
      moduleId = moduleId
    )
  def savedAsModule(newModuleId: Identifier): DatasetEditingDetails =
  {
    DatasetEditsReplaceModule(
      branchId = branchId,
      moduleId = newModuleId
    )
  }
}