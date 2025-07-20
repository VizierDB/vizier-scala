/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
  val jars = mutable.Buffer[URL]()


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


    // This classloader is used only to load the vizier-plugin.json file.  
    // and then discarded    
    val preloader = new URLClassLoader(
      Array(jar.toURI.toURL),
      Vizier.mainClassLoader
    )

    val plugin = 
      Json.parse(
        preloader.getResourceAsStream("vizier-plugin.json")
      ).asOpt[Plugin]
       .getOrElse { 
          throw new RuntimeException(s"$jar is not a valid Vizier Plugin")
       }
 
    Vizier.sparkSession.sparkContext.addJar(jar.getAbsoluteFile().toString)
    val loader = Vizier.sparkSession.sharedState.jarClassLoader
    loader.addURL(url)

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
    jars.append(jar.toURI.toURL)

    val clazz = Class.forName(plugin.plugin_class, true, loader)
    val singleton = clazz.getDeclaredField("MODULE$").get()
    val initMethod = clazz.getMethod("init", Vizier.sparkSession.getClass)

    ClassLoaderUtils.withContextClassLoader(loader) {
      initMethod.invoke(singleton, Vizier.sparkSession)
    }

    return plugin
  }

  def loadedJars = jars.toSeq
}