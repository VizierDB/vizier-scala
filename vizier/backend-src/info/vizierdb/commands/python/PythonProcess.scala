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

import java.io._
import scala.io._
import play.api.libs.json._
import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands.ExecutionContext
import info.vizierdb.Vizier

// Note: Scala does have a ProcessBuilder.  However, Scala's ProcessBuilder
// (inherited from SBT) is optimized for shell-like streaming pipes between 
// independent processes.  It does *very* aggressive buffering, resulting 
// in livelocks when we try to do bi-directional communication.  As a result
// we're going to use the lower level Java process builder here.
import java.lang.{ Process => JProcess, ProcessBuilder => JProcessBuilder}

class PythonProcess(python: JProcess)
  extends LazyLogging
{

  def send(event: String, args: (String, JsValue)*)
  {
    val msg = JsObject( (args :+ ("event" -> JsString(event))).toMap ).toString
    logger.debug(s"Sending: $msg")
    python.getOutputStream.write((msg+"\n").getBytes)
    python.getOutputStream.flush()
  }

  def monitor(handler: JsValue => Unit)(handleError: String => Unit): Int =
  {
    val input = new BufferedReader(new InputStreamReader(python.getInputStream))

    (new Thread(){ 
      override def run(){
        val error = new BufferedReader(new InputStreamReader(python.getErrorStream))
        var line = error.readLine()
        while( line != null ){
          handleError(line)
          line = error.readLine()
        }
      }
    }).start()

    var line: String = input.readLine()
    while( line != null ){
      val event = Json.parse(line)    
      handler(event)
      line = input.readLine()
    }

    return python.waitFor()
  }

  def kill()
  {
    python.destroyForcibly()
  }
}

object PythonProcess 
  extends LazyLogging
{
  var PYTHON_COMMAND = "python3"
  val JAR_PREFIX = "^jar:(.*)!(.*)$".r
  val FILE_PREFIX = "f".r

  def udfBuilder = PythonUDFBuilder(Some(PYTHON_COMMAND))

  def scriptPath: String =
  {
    val resource = getClass().getClassLoader().getResource("__main__.py")
    if(resource == null){
      throw new IOException("Python integration unsupported: __main__.py is unavailable");
    }

    var path = resource.toURI().toString()
    val prefix = "(jar|file):(.*)".r

    logger.debug(s"Base Path: ${path}")
    var done = false;
    while(!done) {
      (prefix findFirstMatchIn path) match {
        case None => done = true
        case Some(hit) => {
          path = hit.group(2)
          logger.debug(s"Prefix: '${hit.group(1)}' Path Now: ${path}")
          hit.group(1) match {
            case "jar" => {
              val splitPoint = path.lastIndexOf('!')
              path = path.substring(0, splitPoint)
              logger.debug(s"Stripping Resource Path; Path Now: ${path}")
            }
            case "file" => {
              return path
            }
          }
        }
      }
    }

    throw new IOException(s"Python integration unsupported: Unknown access method for __main__.py")
  }

  def discoverPython()
  {
    val searchAt = Seq(
      "python3",
      "/usr/bin/python3",
      "/usr/local/bin/python3",
      s"${System.getProperty("user.home")}/.pyenv/bin/python3"
    )
    PYTHON_COMMAND = searchAt.find { test => 
      try {
        val ret = run("print(\"Hi!\")", pythonPath = test)
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

  /**
   * Packages required to use python cells.
   * 
   * The format is: 
   * module_to_test_package_existence -> pypi_package_name
   */
  def REQUIRED_PACKAGES = Seq[(String, String)](
    "numpy"      -> "numpy",
    "bokeh"      -> "bokeh",
    "matplotlib" -> "matplotlib",
    "astor"      -> "astor",
    "pyarrow"    -> "pyarrow<4.0.0",
    "pandas"     -> "pandas",
    "shapely"    -> "shapely",
    "pyspark"    -> "pyspark",
    "PIL"        -> "Pillow"
  )

  def checkPython()
  {
    // no sense checking a non-existent python install
    if(PythonProcess.PYTHON_COMMAND == null) { return }
    val header = 
      """import importlib
        |def testImport(mod, lib):
        |  try:
        |    importlib.import_module(mod)
        |  except:
        |    print(lib)
        |""".stripMargin
    val tests =
      REQUIRED_PACKAGES.map { 
        case (mod, lib) => "testImport(\""+mod+"\",\""+lib+"\")" 
      }

    try {
      val ret = 
        PythonProcess.run(
          (header +: tests).mkString("\n")
        )
      if(ret.length() > 0){
        System.err.println("\nYour installed python is missing dependencies. Python cells may not work properly.")
        System.err.println("\nThe following command will install required dependencies.")
        val deps = ret.split("\n")
                      .map { "'"+_+"'" }
                      .mkString(" ")
        System.err.println(s"  ${PythonProcess.PYTHON_COMMAND} -m pip install $deps")
      }
    } catch {
      case e:Throwable => 
        e.printStackTrace()

    }
  }

  def apply(): PythonProcess =
  {
    val cmd = 
      new JProcessBuilder(PYTHON_COMMAND, scriptPath)

    if(Vizier.config.workingDirectory.isDefined){
      cmd.directory(new File(Vizier.config.workingDirectory()))
    }

    return new PythonProcess(cmd.start())
  }

  def run(script: String, pythonPath: String = PYTHON_COMMAND): String =
  {
    val ret = new StringBuffer()

    val cmd = new JProcessBuilder(pythonPath).start()
    val out = cmd.getOutputStream()
    out.write(script.getBytes())
    out.close()

    Source.fromInputStream(cmd.getInputStream())
          .getLines()
          .mkString("\n")
  }

}

