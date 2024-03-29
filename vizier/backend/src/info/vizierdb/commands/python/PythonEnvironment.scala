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
import info.vizierdb.catalog.Metadata
import scalikejdbc.DBSession
import play.api.libs.json.Json
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.types._
import info.vizierdb.api.FormattedError

trait PythonEnvironment
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

  def invoke(args: Seq[String], context: String = null): String =
  {
    val out = new StringBuilder()
    val err = new StringBuilder()

    val ret = Process(python.toString, args).!(
      ProcessLogger(
        { msg => out.append(msg+"\n") },
        { msg => err.append(msg+"\n") }
      )
    )

    if(ret == 0){ return out.toString }
    else { 
      val msg =
        "Error while "+ 
        Option(context).getOrElse {
          s"running `python ${args.mkString}`"
        }+(if(err.isEmpty){ "" } else { "\n"+err.toString })
      
      logger.warn(msg)
      throw new FormattedError(msg)
    }
  }

  def install(
    packageName: String, 
    version: Option[String] = None, 
    upgrade: Boolean = false
  ): Unit =
  { 
    val spec = s"$packageName${version.map { "=" + _ }.getOrElse("")}"
    logger.debug(s"Installing python package spec: $spec")

    val args = Seq(
      "-m", "pip", "install",
    ) ++ (
      if(upgrade) { Seq(
        "--upgrade", "--upgrade-strategy", "only-if-needed"
      ) } else { Seq.empty }
    ) ++ Seq(spec)

    invoke(args, if(upgrade){ s"upgrading $packageName" } else { s"installing $packageName "})
  }

  def delete(packageName: String): Unit =
  {
    logger.debug(s"Installing python package: $packageName")

    invoke(Seq(
      "-m", "pip", "uninstall", packageName
    ), s"deleting $packageName")    
  }

  lazy val version = 
      fullVersion.split("\\.").take(2).mkString(".")

  lazy val fullVersion =
    Process(Seq(python.toString, "--version")).!!
      .replaceAll("\n", "")
      .split(" ").reverse.head
}

object PythonEnvironment
{
  val SYSTEM_PYTHON_ID = 0l
  val SYSTEM_PYTHON_NAME = "System"

  val INTERNAL_BY_ID = Map[Identifier, InternalPythonEnvironment](
    SYSTEM_PYTHON_ID -> SystemPython
  )
  val INTERNAL_BY_NAME = Map[String, InternalPythonEnvironment](
    SYSTEM_PYTHON_NAME -> SystemPython
  )
}

trait InternalPythonEnvironment extends PythonEnvironment
{
  def summary = 
    serialized.PythonEnvironmentSummary(
      name = PythonEnvironment.SYSTEM_PYTHON_NAME,
      id = PythonEnvironment.SYSTEM_PYTHON_ID,
      revision = 0,
      fullVersion
    )

  def serialize: serialized.PythonEnvironmentDescriptor =
    serialized.PythonEnvironmentDescriptor(
      name = PythonEnvironment.SYSTEM_PYTHON_NAME,
      id = PythonEnvironment.SYSTEM_PYTHON_ID,
      pythonVersion = fullVersion,
      revision = 0,
      packages = packages.map { p => 
        serialized.PythonPackage(p._1, Some(p._2))
      }
    )

}

object SystemPython 
  extends InternalPythonEnvironment
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
        def serializeOuter = serialize
        val ret = PythonProcess.run("print(\"Hi!\")", 
          environment = new PythonEnvironment{
            def python = new File(test)
            def serialize = serializeOuter
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

