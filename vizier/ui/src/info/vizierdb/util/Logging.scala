package info.vizierdb.util

import scala.collection.mutable

trait Logging
{
  lazy val logger = new Logger(getClass.getName)
}

class Logger(loggerName: String)
{
  // println(loggerName)
  val level = idx(Logging.get(loggerName))

  @inline
  private def log(at: Int, msg: => String)
  {
    if(level <= at){
      println(s"[${fromIdx(at)}] ${loggerName}: $msg")
    }
  }
  @inline
  private def idx(l: Logging.Level) = 
    l match {
      case Logging.TRACE => 0
      case Logging.DEBUG => 1
      case Logging.INFO => 2
      case Logging.WARN => 3
      case Logging.ERROR => 4
    }
  @inline
  private def fromIdx(idx: Int): Logging.Level =
    idx match {
      case 0 => Logging.TRACE
      case 1 => Logging.DEBUG
      case 2 => Logging.INFO 
      case 3 => Logging.WARN 
      case _ => Logging.ERROR
    }

  def trace(msg: => String) = log(idx(Logging.TRACE), msg)
  def debug(msg: => String) = log(idx(Logging.DEBUG), msg)
  def info(msg: => String)  = log(idx(Logging.INFO), msg)
  def warn(msg: => String)  = log(idx(Logging.WARN), msg)
  def error(msg: => String) = log(idx(Logging.ERROR), msg)

  override def toString = s"$loggerName -> $level"
}

object Logging extends Enumeration
{
  type Level = Value
  val TRACE,
      DEBUG,
      INFO,
      WARN,
      ERROR = Value

  val DEFAULT = INFO

  val levels = mutable.Map[String, Level](
    "info.vizierdb.ui.network.BranchSubscription" -> INFO,
    "info.vizierdb.test.TestFixtures$MockBranchSubscription$" -> INFO,
    "info.vizierdb.ui.components.dataset.TableView" -> INFO,
    "info.vizierdb.ui.network.SpreadsheetClient" -> TRACE,
    "info.vizierdb.ui.components.TentativeEdits" -> INFO,
  )

  def get(logger: String) = 
    levels.getOrElseUpdate(logger, DEFAULT)

  def set(logger: String, level: Level) =
    levels.put(logger, level)
}