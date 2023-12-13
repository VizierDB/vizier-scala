package info.vizierdb.python

import info.vizierdb.Vizier
import java.io.File
import scala.sys.process._
import info.vizierdb.VizierException

object Pyenv
{
  def exists: Boolean =
    try {
      apply("--version").!!
      return true
    } catch {
      case _:RuntimeException => return false
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
    apply("install", "--list").!!.split("\n").drop(1).map { _.trim() }
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