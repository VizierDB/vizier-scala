/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.ui.components

import scalatags.JsDom.all._
import org.scalajs.dom.html.Element
import info.vizierdb.serialized.PackageCommand
import rx._
import info.vizierdb.ui.components.editors.CodeModuleSummary
import info.vizierdb.ui.components.editors.SpreadsheetModuleSummary

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
      case ("sql", "query")        => Some(new CodeModuleSummary(module, "sql"))
      case ("script", "python")    => Some(new CodeModuleSummary(module, "python"))
      case ("script", "scala")     => Some(new CodeModuleSummary(module, "scala"))
      case ("data", "spreadsheet") => Some(new SpreadsheetModuleSummary(module))
      case _ => None
    }
}