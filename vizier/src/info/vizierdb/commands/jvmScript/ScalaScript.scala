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
package info.vizierdb.commands.jvmScript

// import javax.script.ScriptEngineManager
import javax.script._

import collection.JavaConverters._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{ IMain, ISettings }
import scala.tools.nsc.interpreter.{ Scripted, ReplReporter }
import scala.reflect.internal.util.Position
import scala.collection.JavaConverters._
import info.vizierdb.commands._
import java.io.{ PrintWriter, Reader }
import scala.beans.BeanProperty

import scala.reflect.runtime._
import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox

object ScalaScript extends Command
{

  val STANDARD_PREFIX = 
    """import info.vizierdb.commands.jvmScript.ScalaScript
      |val vizierdb = ScalaScript.myExecutionContext
      |def print(msg:Any) = vizierdb.message(msg.toString)
      |def println(msg:Any) = vizierdb.message(msg.toString+"\n")
      |""".stripMargin

  private val executionContext = new ThreadLocal[ExecutionContext]

  def myExecutionContext = executionContext.get()

  // Java provides a ScriptEngine interface that might be a much better way to 
  // implement this cell.  See:
  //   - https://stackoverflow.com/questions/38064841/using-scala-toolbox-eval-how-do-i-define-i-value-i-can-use-in-later-evals
  // It's not really well documented, and unfortunately there seems to be a Scala 2.12
  // bug keeping it from working properly.  See:
  //   - https://gist.github.com/ScalaWilliam/29f09ca77f11209aeaeb92f83e553087
  //   - https://github.com/scala/bug/issues/10488
  // Once we move to 2.13 (after Spark adds support), this should work.

  def eval(script: String, context: ExecutionContext): Unit =
  {
    val toolbox = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()
    val tree = toolbox.parse(STANDARD_PREFIX + "\n" + script)
    executionContext.set(context)
    toolbox.eval(tree)
    executionContext.set(null)
  }

  def name = "Scala"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "scala", name = "Scala Code"),
  )
  def format(arguments: Arguments): String = 
    arguments.pretty("source")
  def title(arguments: Arguments): String = 
    "Scala Code"

  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    try {
      eval(arguments.get[String]("source"), context)
    } catch {
      case e: scala.tools.reflect.ToolBoxError => 
        context.error(e.getMessage()) // sadly no line numbers here
    }
  }

  def predictProvenance(arguments: Arguments) = None


}

