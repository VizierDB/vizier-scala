package info.vizierdb.ui.components.dataset

import rx._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.types.Identifier
import info.vizierdb.ui.network.SpreadsheetClient
import info.vizierdb.ui.Vizier

class StandaloneSpreadsheetView(
  datasetId: Identifier,
  projectId: Identifier,
  var editingDetails: DatasetEditingDetails
)(implicit owner: Ctx.Owner)
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val cli = new SpreadsheetClient(projectId, datasetId, Vizier.api)
  cli.connected.trigger { connected => 
    if(connected){ cli.subscribe(0) }
  }
  val table = new TableView(cli, 
      rowHeight = 30,
      maxHeight = 400,
      headerHeight = 40
  )
  cli.table = Some(table)

  val root = div(
    `class` := "standalone_spreadsheet",
    div(
      `class` := "header",
      button(
        onclick := { _:(dom.Event) =>
          editingDetails match {
            case DatasetNotEditable => 
              Vizier.error("Spreadsheet opened read-only")
            case DatasetEditsAfterModule(branchId, moduleId) =>
              val oldEditingDetails = editingDetails
              editingDetails = DatasetNotEditable
              cli.saveAfter(branchId = branchId, moduleId = moduleId)
                 .onSuccess { case id => editingDetails = oldEditingDetails.savedAsModule(id) }
            case DatasetEditsReplaceModule(branchId, moduleId) =>
              val oldEditingDetails = editingDetails
              editingDetails = DatasetNotEditable
              cli.saveAs(branchId = branchId, moduleId = moduleId)
                 .onSuccess { case id => editingDetails = oldEditingDetails.savedAsModule(id) }
          }
        },
        "Save"
      )
    ),
    table.root
  ).render
}