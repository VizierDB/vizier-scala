package info.vizierdb.ui.view

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.state.BranchDescription
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.rxExtras.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }

class BranchView(projectId: String, branch: BranchDescription)
                (implicit owner: Ctx.Owner, data: Ctx.Data)
{

}