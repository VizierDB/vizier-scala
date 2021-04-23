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
package info.vizierdb.commands.data

import info.vizierdb.commands._
import info.vizierdb.Vizier
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.types.ArtifactType
import java.net.URL
import java.nio.file.{ Files, Paths }


object UnloadFile extends Command
  with LazyLogging
{
  val FILE = "file"
  val PATH = "path"

  def name: String = "Unload File"
  def parameters: Seq[Parameter] = Seq(
    ArtifactParameter(id = FILE, name = "File", artifactType = ArtifactType.FILE),
    StringParameter(id = PATH, name = "Path"),
  )
  def format(arguments: Arguments): String = 
    s"UNLOAD ${arguments.pretty(FILE)} TO ${arguments.pretty(PATH)}"
  def title(arguments: Arguments): String = 
    s"Unload ${arguments.pretty(FILE)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val fileName = arguments.get[String](FILE)
    val fileArtifact = context.artifact(fileName)
                              .getOrElse{ 
                                context.error(s"Dataset $fileName does not exist"); return
                              }

    val path = arguments.get[String](PATH)
    val url = if(path.size > 0 && path(0) == '/'){
                new URL("file://"+path)
              } else { new URL(path) }

    url.getProtocol() match {
      case "file" if Vizier.config.serverMode() => {
        throw new RuntimeException("Writing to the local file system is disabled in server mode")
      }
      case "file"  => {
        val source = fileArtifact.file.toPath()
        val destination = Paths.get(url.getPath)
        Files.copy(source, destination)
        context.message(s"Copied $fileName (id = ${fileArtifact.id}) to $destination")
      }
      case _ => {
        throw new RuntimeException(s"Invalid protocol: ${url.getProtocol()}")
      }
    }
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (
      Seq(arguments.get[String](FILE)),
      Seq("file_export")
    ) )
}

