package info.vizierdb

import play.api.libs.json._
import java.net.URLClassLoader
import java.net.URL
import java.io.File
import java.io.FileNotFoundException
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable
import info.vizierdb.util.ClassLoaderUtils

case class Plugin(
  name: String,
  schema_version: Int,
  plugin_class: String,
  description: String,
  documentation: Option[String],
)
{
} 

object Plugin
  extends LazyLogging
{

  val loaded = mutable.Map[String, Plugin]()


  implicit val pluginFormat: Format[Plugin] = Json.format

  def load(file: File): Plugin =
  {
    val jar = 
      if(file.isAbsolute){ file }
      else { 
        Vizier.config.workingDirectoryFile.toPath
              .resolve(file.toPath)
              .toFile 
      }

    if(!jar.exists()){ throw new FileNotFoundException(jar.getAbsoluteFile.toString) }
    val url = jar.getAbsoluteFile().toURI().toURL()

    // It feels like a bit of a hack to just stack plugins onto the currently running
    // ClassLoader... but this seems to be the only way to make the loaded classes visible
    // to the running spark instance.
    val loader = new URLClassLoader(
      Array(jar.toURI.toURL),
      Thread.currentThread().getContextClassLoader()
    )
    Thread.currentThread().setContextClassLoader(loader)

    val plugin = 
      Json.parse(
        loader.getResourceAsStream("vizier-plugin.json")
      ).asOpt[Plugin]
       .getOrElse { 
          throw new RuntimeException(s"$jar is not a valid Vizier Plugin")
       }
 
    val detail = s"${plugin.name} [$jar]"

    assert(
      plugin.schema_version > 0 && plugin.schema_version <= 1,
      s"Unsupported version '${plugin.schema_version} for plugin $detail"
    )

    assert(
      !loaded.contains(plugin.name),
      s"Plugin ${plugin.name} is already loaded."
    )

    loaded.put(plugin.name, plugin)

    val clazz = Class.forName(plugin.plugin_class, true, loader)
    val singleton = clazz.getDeclaredField("MODULE$").get()
    val initMethod = clazz.getMethod("init", Vizier.sparkSession.getClass)

    ClassLoaderUtils.withContextClassLoader(loader) {
      initMethod.invoke(singleton, Vizier.sparkSession)
    }

    return plugin
  }
}