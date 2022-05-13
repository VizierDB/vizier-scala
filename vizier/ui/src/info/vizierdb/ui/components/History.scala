package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scala.util.{ Failure, Success }
import info.vizierdb.ui.network.API
import info.vizierdb.serialized
import info.vizierdb.types._
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.FontAwesome


class History(
  projectId: Identifier, 
  initialBranchId: Option[Identifier], 
  branches: Rx[Seq[(String, Identifier)]]
)(implicit owner: Ctx.Owner)
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def this(project: Project)(implicit owner: Ctx.Owner) =
  {
    this(
      project.projectId, 
      project.activeBranch.now.orElse { project.branches.now.headOption.map { _._1 } },
      project.branches.map { _.toSeq.map { case (_, b) => (b.name, b.id) } }
    )
  }

  val branchId = Var(initialBranchId)
  val branch = Var[Option[serialized.BranchDescription]](None)
  branchId.trigger { _ match {
    case None => branch() = None
    case Some(branchId) =>
      Vizier.api.branchGet(projectId, branchId)
         .onComplete { 
            case Success(branch) => 
              this.branch() = Some(branch)
            case Failure(err) => 
              err.printStackTrace()
              Vizier.error(err.getMessage())
         }
  }}

  val root = div(
    `class` := "history",
    branch.map { 
      case None => Spinner().render
      case Some(branch) => 
        div(
          h3(branch.name),
          branch.workflows.map { wf =>
            val action = ActionType.decode(wf.action)
            div(
              `class` := s"workflow ${action}",
              FontAwesome(ActionType.icon(action)),
              span(`class` := "description", 
                action.toString, " ", (wf.packageId ++ wf.commandId).mkString(".")
              ),
              a(
                href := Vizier.links.workflow(projectId, branch.id, wf.id),
                target := "_blank",
                FontAwesome("share-square-o")
              )
            )
          }
        ).render
    }.reactive
  ).render
}