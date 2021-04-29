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
import org.mimirdb.api.{ MimirAPI, MimirConfig }

class Config(arguments: Seq[String]) 
  extends ScallopConf(arguments)
  with LazyLogging
{
  version("Vizier-Scala 1.1.0 (c) 2021 U. Buffalo, NYU, Ill. Inst. Tech., and Breadcrumb Analytics")
  banner("""Docs: https://github.com/VizierDB/vizier-scala/wiki
           |Usage: vizier [OPTIONS]
           |    or vizier import [OPTIONS] export
           |""".stripMargin)
  val defaults = Config.loadDefaults()

  val help = opt[Boolean]("help",
    descr = "Display this help message",
    default = Option(false)
  )

  val googleAPIKey = opt[String]("google-api-key", 
    descr = "Your Google API Key (for Geocoding)",
    default = Option(defaults.getProperty("google-api-key")),
    noshort = true
  )
  val osmServer = opt[String]("osm-server",
    descr = "Your Open Street Maps server (for Geocoding)",
    default = Option(defaults.getProperty("osm-server")),
    noshort = true
  )
  val basePath = opt[File]("database",
    descr = "Path to the project database (e.g., vizier.db)",
    default = Some(new File("vizier.db"))
  )
  val port = opt[Int]("port",
    descr = "The port to run on (default: 5000)",
    default = Option(defaults.getProperty("vizier-port"))
                  .map { _.toInt }
                  .orElse { Some(5000) }
  )
  val pythonPath = opt[String]("python", 
    descr = "Path to python binary",
    default = 
      Option(defaults.getProperty("python"))
          .orElse { Some(info.vizierdb.commands.python.PythonProcess.PYTHON_COMMAND) }
  )
  val publicURL = opt[String]("public-url",
    descr = "The Public-Facing URL of Vizier (e.g., for use with proxies)",
    default = Option(defaults.getProperty("vizier-public-url"))
  )
  val experimental = opt[List[String]]("experiment", 
    short = 'X',
    descr = "Enable an experimental option",
    default = Some(List[String]()))

  val devel = opt[Boolean]("devel", 
    descr = "Launch vizier in development mode (bind to all ports, permissive CORS headers)",
    default = Option(defaults.getProperty("vizier-developer-mode"))
                .map { _.toLowerCase.equals("true") }
                .orElse { Some(false) }
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

  object ingest extends Subcommand("import", "ingest") {
    val execute = toggle("execute", default = Some(true))
    val file = trailArg[File]("export", 
      descr = "The exported vizier file"
    )
  }

  addSubcommand(ingest)
  
  object export extends Subcommand("export") {
    val projectId = trailArg[Long]("project-id",
      descr = "The identifier of the project to export"
    )
    val file = trailArg[File]("export", 
      descr = "The file to export to"
    )
  }
  addSubcommand(export)


  verify()

  def getMimirConfig: MimirConfig =
  {
    val config = 
      new MimirConfig(Seq(
        "--python", pythonPath(),
        "--data-dir", basePath().toString,
      ))
    config.verify()
    return config
  }
  def setMimirConfig = { MimirAPI.conf = getMimirConfig }

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

