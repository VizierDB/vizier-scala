package info.vizierdb.ui.roots

import org.scalajs.dom
import org.scalajs.dom.document
import info.vizierdb.ui.rxExtras.OnMount
import rx._
import scalatags.JsDom.all._
import info.vizierdb.ui.network.SpreadsheetClient
import info.vizierdb.api.spreadsheet.OpenDataset
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.ui.Vizier

object Spreadsheet
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val datasetId = arguments.get("dataset").get.toLong
    val branchId = arguments.get("branch").map { _.toLong }

    val cli = new SpreadsheetClient(OpenDataset(projectId, datasetId), Vizier.api)
    cli.connected.trigger { connected => 
      if(connected){ cli.subscribe(0) }
    }
    val table = new TableView(cli, 
        rowHeight = 30,
        maxHeight = 400,
        headerHeight = 40
    )
    cli.table = Some(table)

    val body = div(
      `class` := "standalone_spreadsheet",
      div(
        `class` := "header",
        button(
          onclick := { _:(dom.Event) =>
            cli.save()
          },
          "Save"
        )
      ),
      table.root
    ).render

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(body)
      OnMount.trigger(document.body)
    })
  }

}