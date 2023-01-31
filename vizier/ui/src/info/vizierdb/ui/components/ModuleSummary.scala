package info.vizierdb.ui.components

import scalatags.JsDom.all._
import org.scalajs.dom.html.Element
import info.vizierdb.serialized.PackageCommand
import rx._
import info.vizierdb.ui.components.summary.CodeModuleSummary

trait ModuleSummary
{
  val root: Frag

  def editor(packageId: String, command: PackageCommand, delegate: ModuleEditorDelegate)(implicit owner: Ctx.Owner): ModuleEditor = 
    ModuleEditor(packageId, command, delegate)

  def endEditor(): Unit = ()
}

object ModuleSummary
{
  def apply(module: Module)(implicit owner: Ctx.Owner): Option[ModuleSummary] =
    (module.subscription.packageId, module.subscription.commandId) match {
      case ("sql", "query")     => Some(new CodeModuleSummary(module, "sql"))
      case ("script", "python") => Some(new CodeModuleSummary(module, "python"))
      case ("script", "scala")  => Some(new CodeModuleSummary(module, "scala"))
      case _ => None
    }
}