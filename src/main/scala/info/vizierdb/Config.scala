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
  val basePath = opt[String]("project",
    descr = "Path to the project (e.g., vizier.db)",
    default = Some("vizier.db")
  )
  val experimental = opt[List[String]]("X", default = Some(List[String]()))

  verify()


  def getMimirConfig: MimirConfig =
  {
    val config = 
      new MimirConfig(Seq(
        "--python", pythonPath(),
        "--data-dir", new File(basePath(), "mimir_data").toString,
        "--staging-dir", basePath()
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