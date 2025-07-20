/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.util

import scala.collection.mutable
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

trait Logging
{
  lazy val logger = Logging.logger(getClass.getName)
}

class Logger(val loggerName: String, var level: Int)
{

  def this(loggerName: String, level: Logging.Level) =
    this(loggerName, Logging.toIdx(level))

  def set(newLevel: Logging.Level) = 
    level = Logging.toIdx(newLevel)

  def get = Logging.fromIdx(level)

  @inline
  private def log(at: Int, msg: => String)
  {
    if(level <= at){
      println(s"[${Logging.fromIdx(at)}] ${loggerName}: $msg")
    }
  }

  def trace(msg: => String) = log(Logging.toIdx(Logging.TRACE), msg)
  def debug(msg: => String) = log(Logging.toIdx(Logging.DEBUG), msg)
  def info(msg: => String)  = log(Logging.toIdx(Logging.INFO), msg)
  def warn(msg: => String)  = log(Logging.toIdx(Logging.WARN), msg)
  def error(msg: => String) = log(Logging.toIdx(Logging.ERROR), msg)

  override def toString = s"$loggerName -> $level"
}

@JSExportTopLevel("Logging")
object Logging extends Enumeration
{
  type Level = Value
  val TRACE,
      DEBUG,
      INFO,
      WARN,
      ERROR = Value

  val DEFAULT = INFO

  val defaultLevels = Map[String, Level](
    "info.vizierdb.ui.network.BranchSubscription" -> INFO,
    "info.vizierdb.test.TestFixtures$MockBranchSubscription$" -> INFO,
    "info.vizierdb.ui.components.dataset.TableView" -> INFO,
    "info.vizierdb.ui.network.SpreadsheetClient" -> DEBUG,
    "info.vizierdb.ui.components.TentativeEdits" -> INFO,
    "info.vizierdb.ui.components.DefaultModuleEditor" -> INFO,
    "info.vizierdb.ui.components.Module" -> INFO,
    "info.vizierdb.ui.components.TentativeModule" -> INFO,
    "info.vizierdb.ui.components.TentativeEdits$Tail$" -> INFO,
  )

  @inline
  private[util] def toIdx(l: Logging.Level) = 
    l match {
      case Logging.TRACE => 0
      case Logging.DEBUG => 1
      case Logging.INFO => 2
      case Logging.WARN => 3
      case Logging.ERROR => 4
    }
  @inline
  private[util] def fromIdx(idx: Int): Logging.Level =
    idx match {
      case 0 => Logging.TRACE
      case 1 => Logging.DEBUG
      case 2 => Logging.INFO 
      case 3 => Logging.WARN 
      case _ => Logging.ERROR
    }

  val loggers = mutable.Map[String, Logger]()

  def logger(name: String): Logger = 
    loggers.getOrElseUpdate(name, 
      new Logger(name, defaultLevels.getOrElse(name, DEFAULT)))

  def get(name: String): Level = 
    logger(name).get

  def set(name: String, level: Level) =
    logger(name).set(level)

  @JSExport("enable")
  def enable(name: String) =
    set(name, TRACE)

  @JSExport("disable")
  def disable(name: String) =
    set(name, ERROR)

  def test[T](name: String, level: Level)(op: => T): T =
  {
    val old = get(name)
    set(name, level)
    val ret = op
    set(name, old)
    return ret
  }

  def trace[T](names: String*)(op: => T): T =
  {
    val old = names.map { x => x -> get(x) }
    names.foreach { set(_, TRACE) }
    val ret = op
    old.foreach { case (logger, oldlvl) => set(logger, oldlvl) }
    return ret
  }

  def debug[T](names: String*)(op: => T): T =
  {
    val old = names.map { x => x -> get(x) }
    names.foreach { set(_, DEBUG) }
    val ret = op
    old.foreach { case (logger, oldlvl) => set(logger, oldlvl) }
    return ret
  }

}