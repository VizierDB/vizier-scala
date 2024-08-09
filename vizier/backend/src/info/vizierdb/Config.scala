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
package info.vizierdb

import java.util.Properties
import java.io.File
import org.rogach.scallop._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.Cell
import scala.io.Source
import java.net.URL
import scala.collection.convert.ImplicitConversions._

class Config(arguments: Seq[String]) 
  extends ScallopConf(arguments)
  with LazyLogging
{
  lazy val VERSION:String =
    {
      Source.fromInputStream(
        getClass().getClassLoader()
                  .getResourceAsStream("vizier-version.txt")
      ).getLines.next
    } 

  version(s"Vizier-Scala $VERSION")
  banner("""    (c) 2021 U. Buffalo, NYU, Ill. Inst. Tech., and Breadcrumb Analytics
           |  Docs:        https://github.com/VizierDB/vizier-scala/wiki
           |  Bug Reports: https://github.com/VizierDB/vizier-scala/issues
           |Usage: vizier [OPTIONS]
           |    or vizier import [OPTIONS] export
           |""".stripMargin)

  /// STOP: Defaults is meant to be used only within this file.  
  /// You probably want to use `Vizier.getProperty` instead
  private val defaults = Config.loadDefaults()

  val help = opt[Boolean]("help",
    descr = "Display this help message",
    default = Option(false)
  )

  val basePath = opt[File]("database",
    descr = "Path to the project database (e.g., vizier.db)",
    default = Some(new File("vizier.db"))
  )
  val port = opt[Int]("port",
    descr = "The port to run on (default: 5050)",
    default = Option(defaults.getProperty("vizier-port"))
                  .map { _.toInt }
                  .orElse { Some(5050) }
  )
  val pythonPath = opt[String]("python", 
    descr = "Path to python binary (default: search for one)",
    default = 
      Option(defaults.getProperty("python"))
  )
  val publicURL = opt[String]("public-url",
    descr = "The Public-Facing URL of Vizier (e.g., for use with proxies)",
    default = Option(defaults.getProperty("vizier-public-url"))
  )
  val experimental = opt[List[String]]("experiment", 
    short = 'X',
    descr = "Enable an experimental option",
    default = Some(List[String]()))

  val commandLineProperties = props[String]('E',
    descr = "Enable a property (e.g., for plugin configuration)"
  )
  lazy val properties = 
    defaults.entrySet
            .map { a => a.getKey.toString -> a.getValue.toString } 
            .toMap ++
            commandLineProperties

  val devel = opt[Boolean]("devel", 
    descr = "Launch vizier in development mode (bind to all ports, permissive CORS headers)",
    default = Option(defaults.getProperty("vizier-developer-mode"))
                .map { _.toLowerCase.equals("true") }
                .orElse { Some(false) }
  )

  val noUI = opt[Boolean]("no-ui",
    descr = "Don't auto-launch the UI on startup",
    default = Some(false)
  )

  val connectFromAnyHost = opt[Boolean]("connect-from-any-host",
    descr = "Allow connections from any IP (WARNING: this will let anyone on your network run code on your machine)",
    default = Option(false),
    noshort = true
  )

  val serverMode = opt[Boolean]("server",
    descr = "Disable features that assume Vizier is running under the account of the user interacting with it",
    default = Option(false),
    noshort = true
  )

  val workingDirectory = opt[String]("working-directory",
    descr = "Override the current working directory for relative file paths",
    default = None
  )

  val supervisorThreads = opt[Int]("supervisor-threads",
    descr = "Configure the number of supervisor threads (concurrently executing workflows; default 2)",
    default = Some(
      Option(defaults.getProperty("supervisor-threads"))
        .map { _.toInt }
        .getOrElse { 3 },
    )
  )

  val workerThreads = opt[Int]("worker-threads",
    descr = "Configure the number of worker threads (concurrently executing cells; default 5)",
    default = Some(
      Option(defaults.getProperty("worker-threads"))
        .map { _.toInt }
        .getOrElse { 9 }
    ),
  )

  val cacheDirOverride = opt[File]("cache-dir",
    descr = "Set vizier's cache directory (default ./.vizier-cache)"
  )

  val warehouseDirOverride = opt[File]("spark-warehouse-dir",
    descr = "Set the SparkSQL warehouse directory (default: {cache-dir}/spark-warehouse)"
  )

  val extraPlugins = opt[List[File]]("plugin",
    short = 'P',
    descr = "Enable a plugin for this session",
    default = Some(List())
  )

  lazy val plugins:Seq[File] = 
    Option(defaults.getProperty("vizier-plugins"))
        .map { _.split(":").map { new File(_) }.toSeq }
        .getOrElse { Seq() } ++ extraPlugins()

  def workingDirectoryFile: File = 
    new File(
      workingDirectory
        .orElse { Option(System.getenv("user.dir")) }
        .getOrElse("."))

  def workingDirectoryURL: URL =
  {
    var path = workingDirectoryFile.getAbsoluteFile().toString
    if(!path.endsWith("/")) { path = path + "/"; }
    new URL(s"file://${path}")
  }
  
  lazy val cacheDirFile = 
    cacheDirOverride.getOrElse { 
                      new File(workingDirectoryFile, ".vizier-cache")
                    }

  val sparkHost = opt[String]("spark-host",
    descr = "Spark master node",
    default = Some("local")
  )

  // Aliases for later use
  lazy val dataDir = basePath().toString + File.separator + "data"
  val stagingDir = "staging"
  val stagingDirIsRelativeToDataDir = true
  lazy val dataDirFile = new File(dataDir)
  lazy val pythonVenvDirFile = new File(cacheDirFile, "python")

  def resolveToDataDir(path: String) = { new File(dataDirFile, path).getAbsoluteFile }
  def resolveToWorkingDir(path: File): File =
    if(path.isAbsolute()) { path }
    else { workingDirectoryFile.toPath.resolve(path.toPath).toFile }

  ////////////////////////// Ingest //////////////////////////

  object ingest extends Subcommand("import", "ingest") {
    val execute = toggle("execute", default = Some(true))
    val file = trailArg[File]("export", 
      descr = "The exported vizier file"
    )
  }
  addSubcommand(ingest)
  
  ////////////////////////// Export //////////////////////////
  object export extends Subcommand("export") {
    val projectId = trailArg[Long]("project-id",
      descr = "The identifier of the project to export"
    )
    val file = trailArg[File]("export", 
      descr = "The file to export to"
    )
  }
  addSubcommand(export)
  
  //////////////////////////////////////////////////////
  
  object run extends Subcommand("run") {
    val project = trailArg[String]("project",
      descr = "The name or identifier of the project run"
    )
    val branch = opt[String]("branch", short = 'b',
      descr = "The branch to re-execute (default = head)"
    )
    val cells = opt[List[Cell.Position]]("cell", short = 'c',
      descr = "One or more cells to force re-execution on (default = all)",
      default = Some(List.empty)
    )
    val showCaveats = toggle("show-caveats", noshort = true, default = Some(true),
      descrYes = "Compute and print caveats for every dataset artifact at the end of the trace.  Return an error code if any exist."
    )
  }
  addSubcommand(run)

  //////////////////////////////////////////////////////

  ////////////////////////// Doctor //////////////////////////
  object doctor extends Subcommand("doctor") {

  }
  addSubcommand(doctor)

  //////////////////////////////////////////////////////

  object garbageCollect extends Subcommand("gc") {

  }
  addSubcommand(garbageCollect)

  //////////////////////////////////////////////////////

  verify()

  def commandOrSubcommandNeedsMimir: Boolean =
  {
    if(!subcommand.isDefined){ return true }
    else if(subcommand.equals(run)){ return true }
    else { return false }
  }

  def setProperty(key: String, value: String) = 
  {
    defaults.setProperty(key, value)
  }

}

object Config
  extends LazyLogging
{
  def apply(arguments: Seq[String]) = new Config(arguments)

  def loadDefaults(): Properties =
  {
    import java.nio.file.{ Paths, Files }

    val properties = new Properties()
    val home = Paths.get(System.getProperty("user.home"))
    val potentialLocations = Seq(
      home.resolve(".vizierdb"),
      home.resolve(".config").resolve("vizierdb.conf"),
    )
    for(loc <- potentialLocations){
      logger.trace(s"Checking $loc")
      if(Files.exists(loc)){
        logger.debug(s"Adding global config at $loc")
        properties.load(new java.io.FileReader(loc.toFile()))
      } else {
        logger.trace(s"$loc does not exist")
      }
    }
    return properties
  }

}

