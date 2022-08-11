package info.vizierdb.commands.python

import info.vizierdb.Vizier
import scala.sys.process._
import scala.io._

// Note: Scala does have a ProcessBuilder.  However, Scala's ProcessBuilder
// (inherited from SBT) is optimized for shell-like streaming pipes between 
// independent processes.  It does *very* aggressive buffering, resulting 
// in livelocks when we try to do bi-directional communication.  As a result
// we're going to use the lower level Java process builder here.
import java.lang.{ Process => JProcess, ProcessBuilder => JProcessBuilder}
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import info.vizierdb.VizierException

sealed trait PythonEnvironment
  extends LazyLogging
{
  def python: File

  def packages: Seq[(String, String)] = 
  {
    val raw = 
      Process(python.toString, Seq(
        "-m", "pip", "list"
      ))
      // Pip prints warnings if it's even slightly out of date... disable those
      .lineStream(ProcessLogger(line => (), line => ()))
      .toIndexedSeq

    // pip list outputs a header of the form
    // ```
    // Package          Version
    // ---------------- ------------
    // ```
    // We're going to get the '----'s and figure out how wide they are to safely split
    // the subsequent lines.
    val split = raw(1).split(" ")
    val splitPoint = split(0).size+1

    raw.drop(2).map { line => 
      val (name, version) = line.splitAt(splitPoint)

      ( name.trim(), version.trim() )
    }
  }

  def install(packageName: String, version: Option[String] = None): Unit =
  { 
    val spec = s"$packageName${version.map { "=" + _ }.getOrElse("")}"
    logger.debug(s"Installing python package spec: $spec")

    Process(python.toString, Seq(
      "-m", "pip", "install", spec
    )).!!
    // Pip prints warnings if it's even slightly out of date... disable those
    // .lineStream(ProcessLogger(logger.info(_), logger.error(_)))
  }

  lazy val version = 
      fullVersion.split("\\.").take(2).mkString(".")

  lazy val fullVersion =
    Process(Seq(python.toString, "--version")).!!
      .replaceAll("\n", "")
      .split(" ").reverse.head
}

object SystemPython 
  extends PythonEnvironment
  with LazyLogging
{
  val python = 
    new File(
      if(Vizier.config.pythonPath.isSupplied){
        Vizier.config.pythonPath():String
      } else {
        discoverPython()
      }
    )

  def discoverPython(): String =
  {
    val searchAt = Seq(
      "python3",
      "/usr/bin/python3",
      "/usr/local/bin/python3",
      s"${System.getProperty("user.home")}/.pyenv/bin/python3"
    )
    searchAt.find { test => 
      try {
        val ret = PythonProcess.run("print(\"Hi!\")", 
          environment = new PythonEnvironment{
            def python = new File(test)
          }
        )
        logger.trace(s"Discovering Python ($test -> '$ret')")
        ret.equals("Hi!")
      } catch {
        case e:Exception => false
      }
    }.getOrElse {
      System.err.println("\nUnable to find a working python.  Python cells will not work.")
      System.err.println("\nInstall python, or launch vizier with:")
      System.err.println("  vizier --python path/to/your/python")
      System.err.println("or add the following line (without quotes) to ~/.vizierdb or ~/.config/vizierdb.conf")
      System.err.println("  \"python=path/to/your/python\"")
      null
    }
  }
}

case class VirtualPython(env: String, targetVersion: String)
  extends PythonEnvironment
  with LazyLogging
{
  def dir: File = 
    new File(Vizier.config.pythonVenvDirFile, env)

  def bin: File =
    new File(dir, "bin")

  def python: File =
    new File(bin, "python3")

  def exists = dir.exists()

  def init(overwrite: Boolean = false, fallBackToSystemPython: Boolean = false): Unit =
  {
    // we need a python binary of the right version to bootstrap the venv
    val bootstrapBinary = 
      if(SystemPython.fullVersion == targetVersion){
        SystemPython.python.toString()
      } else if(Pyenv.exists && (Pyenv.installed contains targetVersion)) {
        // if the system python is not right, try pyenv
        Pyenv.python(targetVersion)
      } else if(fallBackToSystemPython) {
        logger.warn(s"Python version '$targetVersion' is not installed; Falling back to system python")
        SystemPython.python.toString()
      } else {
        throw new VizierException(s"Trying to create virtual environment for non-installed python version '$targetVersion'")
      }

    logger.info(s"Bootstrapping venv $env with $bootstrapBinary")

    var args = 
      Seq(
        "-m", "venv",
        "--upgrade-deps",
        "--copies",
      )
    if(overwrite){ args = args :+ "--clear" }

    args = args :+ dir.toString

    Process(bootstrapBinary, args).!!
  }
}