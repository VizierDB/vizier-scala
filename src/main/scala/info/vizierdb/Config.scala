/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
  version("Vizier-Scala 0.2 (c) 2020 Univ. at Buffalo, New York Univ., and Illinois Inst. of Tech.")
  banner("""Usage: vizier [OPTIONS]
           |    or vizier import [OPTIONS] export
           |""".stripMargin)
  val defaults = Config.loadDefaults()

  val googleAPIKey = opt[String]("google-api-key", 
    descr = "Your Google API Key (for Geocoding)",
    default = Option(defaults.getProperty("google-api-key"))
  )
  val osmServer = opt[String]("osm-server",
    descr = "Your Open Street Maps server (for Geocoding)",
    default = Option(defaults.getProperty("osm-server"))
  )
  val pythonPath = opt[String]("python", 
    descr = "Path to python binary",
    default = 
      Option(defaults.getProperty("python"))
          .orElse { Some(info.vizierdb.commands.python.PythonProcess.PYTHON_COMMAND) }
  )
  val basePath = opt[File]("project",
    descr = "Path to the project (e.g., vizier.db)",
    default = Some(new File("vizier.db"))
  )
  val experimental = opt[List[String]]("X", 
    descr = "Enable an experimental option",
    default = Some(List[String]()))

  object ingest extends Subcommand("import", "ingest") {
    val execute = toggle("execute", default = Some(true))
    val file = trailArg[File]("export", 
      descr = "The exported vizier file"
    )
  }
  addSubcommand(ingest)


  verify()


  def getMimirConfig: MimirConfig =
  {
    val config = 
      new MimirConfig(Seq(
        "--python", pythonPath(),
        "--data-dir", new File(basePath(), "mimir_data").toString,
        "--staging-dir", basePath().toString
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
