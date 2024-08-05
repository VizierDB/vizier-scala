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
package info.vizierdb.commands.python

import info.vizierdb.Vizier
import java.io.File
import scala.sys.process._
import info.vizierdb.VizierException
import java.io.IOException

object Pyenv
{
  def exists: Boolean =
    try {
      apply("--version").!!
      return true
    } catch {
      case _:RuntimeException => return false
      case _:IOException => return false
    }

  def apply(command: String*) =
    Process("pyenv", command)

  def uninstall(version: String): Unit =
    if(installed contains version){
      apply("uninstall", version).!!
    }

  def install(version: String): Unit =
    apply("install", version).!!

  def versions: Seq[String] =
  {
    try {
      apply("install", "--list").!!.split("\n").drop(1).map { _.trim() }
    } catch {
      case _:IOException => Seq.empty
    }
  } 

  def installed: Seq[String] =
  {
    apply("versions", "--bare").!!.split("\n")
  }

  def python(version: String): String =
  {
    if(installed contains version){
      try {
        Process(Seq("pyenv", "which", "python"), None, "PYENV_VERSION" -> version).!!.trim
      } catch {
        case _:RuntimeException => throw new VizierException(s"Python '$version' is not installed")
      }
    } else {
      throw new VizierException(s"Python '$version' is not installed")
    }
  }
}