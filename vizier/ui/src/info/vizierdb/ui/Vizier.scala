package info.vizierdb.ui

import org.scalajs.dom.document
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URLDecoder
import play.api.libs.json._

import info.vizierdb.api.spreadsheet.OpenDataset
import info.vizierdb.nativeTypes
import info.vizierdb.serialized.ProjectList
import info.vizierdb.serialized.PropertyList
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.ui.components.DisplayArtifact
import info.vizierdb.ui.components.MenuBar
import info.vizierdb.ui.components.Project
import info.vizierdb.ui.components.ProjectListView
import info.vizierdb.ui.components.settings.SettingsView
import info.vizierdb.ui.components.StaticWorkflow
import info.vizierdb.ui.network.{ API, ClientURLs, BranchSubscription, SpreadsheetClient }
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.SystemNotification
import info.vizierdb.ui.widgets.Toast
import info.vizierdb.util.Logging
import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }


/**
 * Methods for initializing each Vizier interface and managing global state
 * 
 * This class serves two roles:
 * 1. It contains a set of methods allowing easy access to global state (e.g., api, links)
 * 2. It contains one method for each 'page' of the UI that connects to the API and sets up
 *    the DOM.
 */
@JSExportTopLevel("Vizier")
object Vizier
  extends Object
  with Logging
{
  implicit val ctx = Ctx.Owner.safe() 

  var api:API = null
  var links:ClientURLs = null

  @JSExport("init")
  def init(url: String = "http://localhost:5000/") =
  {
    api = API(url+"vizier-db/api/v1")
    links = ClientURLs(url)
  }

  lazy val arguments: Map[String, String] = 
    dom.window.location.search
       .substring(1)
       .split("&")
       .map { _.split("=").toSeq }
       .collect { 
          case Seq(k, v) => 
            URLDecoder.decode(k, "UTF-8") ->
              URLDecoder.decode(v, "UTF-8") 
        }
       .toMap

  val project = Var[Option[Project]](None)
  val menu = project.map { _.map { new MenuBar(_) } }

  def error(message: String) =
  {
    Toast(s"ERROR: $message")
    // TODO: We should probably add a warning toast so that the user actually sees 
    //       that something has exploded.
    throw new Exception(message)
  }

  def main(args: Array[String]): Unit = 
  {

  }
  
  @JSExport("project_view")
  def projectView(): Unit = 
  {
    val projectId = 
      arguments.get("project")
               .getOrElse { error("No Project ID specified") }
               .toLong
    val projectRequest = api.projectGet(projectId)
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      try {
        projectRequest
            .onComplete { 
              case Success(response) => 
                project() = Some(new Project(projectId).load(response))
                logger.debug(s"Project: ${project.now.get}")
                document.addEventListener("keydown", { (evt:dom.KeyboardEvent) => 
                  if(evt.key == "Enter" && evt.ctrlKey){
                    project.now.foreach { 
                      _.workflow.now.foreach {
                        _.moduleViewsWithEdits.saveAllCells()
                      }
                    }
                    evt.stopPropagation()
                  } else if (evt.keyCode == 116 /* f5 */) {
                    // disable reload https://github.com/VizierDB/vizier-scala/issues/159
                    evt.preventDefault()
                  // } else {
                  //   println(s"KEY: ${evt.keyCode}")
                  }
                })

                // The following bit can be uncommented for onLoad triggers
                // to automate development debugging
                // dom.window.setTimeout(
                //   () => {
                //     val workflow = 
                //       project.now
                //              .get
                //              .workflow
                //              .now
                //              .get
                //     val module = 
                //       workflow.moduleViewsWithEdits
                //               .prependTentative()
                //     // module.activeView.trigger { _ match {
                //     //   case Some(Left(commandlist)) =>
                //     //     commandlist.simulateClick("data", "load")
                //     //   case _ => 
                //     //     println("Waiting...")
                //     // }}
                //   },
                //   500
                // )

              case Failure(ex) => 
                error(ex.toString)
            }

        document.body.appendChild(
          Rx { project().map { _.root }
                        .getOrElse { Spinner(size = 30) } }.reactive
        )
        OnMount.trigger(document.body)
      } catch {
        case t: Throwable => logger.error(t.toString)
      }
    })
  }

  @JSExport("project_list")
  def projectList(): Unit = 
  {
    val projectList = new ProjectListView()
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild( projectList.root )
      OnMount.trigger(document.body)
    })
  }

  @JSExport("spreadsheet")
  def spreadsheet(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val datasetId = arguments.get("dataset").get.toLong
    val branchId = arguments.get("branch").map { _.toLong }

    val cli = new SpreadsheetClient(OpenDataset(projectId, datasetId), api)
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

  @JSExport("settings")
  def settings(): Unit =
  {
    val settings = new SettingsView(arguments.get("tab"))
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(settings.root)
      OnMount.trigger(document.body)
    })
  }

  @JSExport("artifact")  
  def artifact(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val artifactId = arguments.get("artifact").get.toLong
    val name = arguments.get("name")
    val artifact = api.artifactGet(projectId, artifactId, name = name)

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val root = Var[Frag](div(`class` := "display_artifact", Spinner(50)))

      document.body.appendChild(root.reactive)
      OnMount.trigger(document.body)

      artifact.onComplete { 
        case Success(a) => root() = new DisplayArtifact(a).root
        case Failure(err) => Vizier.error(err.getMessage())
      }
    })
  }

  @JSExport("static_workflow")
  def staticWorkflow(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val branchIdMaybe = arguments.get("branch").map { _.toLong }
    val workflowIdMaybe = arguments.get("workflow").map { _.toLong }

    val branchId = 
      branchIdMaybe.map { Future(_) }
                   .getOrElse { 
                       api.projectGet(projectId)
                          .map { _.defaultBranch }
                   }

    val workflowId =
      workflowIdMaybe.map { Future(_) }
                     .getOrElse { 
                       branchId.flatMap { Vizier.api.branchGet(projectId, _) }
                               .map { _.head.id }
                     }

    val workflow = 
      branchId.flatMap { b => 
        workflowId.flatMap { w => 
          println(s"Getting workflow: ($projectId, $b, $w)")
          api.workflowGet(projectId, b, w)
        }
      }

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val root = Var[Frag](div(`class` := "display_workflow", Spinner(50)))

      document.body.appendChild(root.reactive)
      OnMount.trigger(document.body)

      workflow.onComplete { 
        case Success(w) => 
          println(s"Got workflow: $w")
          root() = new StaticWorkflow(projectId, w).root
        case Failure(err) => Vizier.error(err.getMessage())
      }
    })
  }

}  
