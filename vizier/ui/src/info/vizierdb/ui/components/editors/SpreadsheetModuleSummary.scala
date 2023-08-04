package info.vizierdb.ui.components.editors

import rx._
import info.vizierdb.ui.components.{ Module, ModuleSummary }
import scalatags.JsDom.all._
import info.vizierdb.serialized.ArtifactSummary
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.Vizier
import scala.util.Success
import scala.util.Failure
import info.vizierdb.util.Logging
import info.vizierdb.ui.components.dataset.Dataset
import scala.concurrent.ExecutionContext
import info.vizierdb.types._
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.ui.components.ModuleEditorDelegate
import info.vizierdb.ui.components.ModuleEditor
import info.vizierdb.serialized.CommandArgument
import info.vizierdb.ui.network.SpreadsheetClient
import play.api.libs.json._
import info.vizierdb.api.spreadsheet.SaveWorkflowCell
import info.vizierdb.serialized.CommandArgumentList
import info.vizierdb.api.spreadsheet.OpenWorkflowCell
import info.vizierdb.ui.network.SpreadsheetTools


class SpreadsheetModuleSummary(
  module: Module,
)(implicit owner: Ctx.Owner) 
  extends ModuleSummary
  with Logging
{

  implicit val ec: ExecutionContext = ExecutionContext.global

  val inputName: Rx[Option[String]] = 
    module.subscription
          .arguments
          .map { _.find { _.id == SpreadsheetTools.PARAM_INPUT }
                  .map { _.value.as[String] } }

  val inputDataset: Rx[Option[ArtifactSummary]] =
    Rx { 
      val visibleArtifacts = module.visibleArtifacts()
      inputName().flatMap { visibleArtifacts.get(_) }
                 .map { _._1 }
    }

  val outputName: Rx[Option[String]] = 
    module.subscription
          .arguments
          .map { _.find { _.id == SpreadsheetTools.PARAM_OUTPUT }
                  .flatMap { _.value.asOpt[String].filterNot { _ == "" } } }

  val resultDatasetId: Rx[Option[Identifier]] =
    module.subscription
          .arguments
          .map { _.find { _.id == SpreadsheetTools.PARAM_RESULT_DS } 
                  .flatMap { _.value.asOpt[Identifier] } }

  val serializedSpreadsheet = 
    module.subscription
          .arguments
          .map { _.find { _.id == "spreadsheet" } }

  val dataset = Var[Option[Dataset]](None)

  def loadDataset(artifactId: Identifier): Unit =
  {
    logger.info(s"Spreadsheet fetching dataset: Artifact ${artifactId}")
    Vizier.api.artifactGetDataset(
      projectId = Vizier.project.now.get.projectId,
      artifactId = artifactId,
      offset = Some(0),
      limit = Some(100),
      name = inputName.now
    ).onComplete { 
      case Failure(err) => logger.error(s"Error fetching updated dataset: $err")
      case Success(description) =>
        if(dataset.now.isEmpty) {
          dataset() = Some(new Dataset(description, menu = Seq.empty, onclick = onClickInDataset(_, _)))
        } else {
          dataset.now.get.rebind(description)
        }
    }
  }

  Rx {
    resultDatasetId().orElse { inputDataset().map { _.id } }
  }.trigger { 
    _.foreach { loadDataset(_) }
  }

  var spreadsheetClient: SpreadsheetClient = null

  def onClickInDataset(row: Long, column: Int): Unit =
  {
    logger.info(s"Click at $column[$row]")
    val dataset = 
      this.dataset.now
                  .getOrElse { 
                    logger.error("Internal error: Click in spreadsheet before the spreadsheet has a dataset")
                    return
                  }
    val inputDatasetId = 
      this.inputDataset.now
                       .getOrElse {
                          logger.error("Internal error: Click in spreadsheet before we have a source dataset")
                          return                        
                       }
                       .id
    logger.info(s"Swapping in the spreadsheet: Artifact $inputDatasetId")
    spreadsheetClient = new SpreadsheetClient(
      OpenWorkflowCell(
        projectId = Vizier.project.now.get.projectId,
        branchId = Vizier.project.now.get.branchSubscription.get.branchId,
        moduleId = module.realModuleId.get
      ),
      Vizier.api
    )
    spreadsheetClient.connected.trigger { connected => 
      if(connected){ 
        spreadsheetClient.subscribe(0) 
        spreadsheetClient.startEditing(row, column)
      }
    }
    dataset.setSource(spreadsheetClient)
    module.openEditor()
  }


  val spreadsheetView = 
    dataset.map { _.map { _.root:Frag }
                   .getOrElse { Spinner(20):Frag } 
                }
           .reactive

  override val root: Frag = 
    div(`class` := "spreadsheet_module", spreadsheetView)

  override def editor(packageId: String, command: PackageCommand, delegate: ModuleEditorDelegate)(implicit owner: Ctx.Owner): ModuleEditor = 
  {
    new SpreadsheetEditor(packageId, command, delegate)
  }

  override def endEditor(): Unit =
  {

  }

  class SpreadsheetEditor(val packageId: String, command: PackageCommand, val delegate: ModuleEditorDelegate)
    extends ModuleEditor
  {
    var defaultArguments = Map[String, JsValue]()

    override def loadState(arguments: Seq[CommandArgument]): Unit = 
    {
      defaultArguments = CommandArgumentList.toMap(arguments)

      // Since we've already 'loaded' the spreadsheet, we don't need to do so again.
    }

    override def commandId: String = command.id

    /**
     * Since we're overriding saveState(), currentState should never be called
     */
    override def currentState: Seq[CommandArgument] = Seq.empty

    override val editorFields: Frag = 
      div(spreadsheetView)

    // To avoid unnecessarily copying the spreadsheet state, we're going to load it up here
    override def saveState(): Unit = 
    {
      println("Saving state!")
      delegate.realModuleId match {
        case None => 
          logger.warn("Not allowed to create a new spreadsheet cell")
          return
        case Some(moduleId) => 
          val projectId = Vizier.project.now.get.projectId
          val branchId = Vizier.project.now.get.branchSubscription.get.branchId
          logger.info(s"Saving state to $projectId.$branchId.head.$moduleId")
          spreadsheetClient.send(
            SaveWorkflowCell(
              projectId = projectId,
              branchId = branchId,
              moduleId = moduleId,
              input = inputName.now,
              output = outputName.now.filterNot { _ == "" }
                                 .orElse { inputName.now }
            )
          )
          spreadsheetClient.close()
      }
    }

  }
}