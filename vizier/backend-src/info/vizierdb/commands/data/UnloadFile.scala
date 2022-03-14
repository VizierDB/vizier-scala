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
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject


object UnloadFile extends Command
  with LazyLogging
{
  val FILE      = "file"
  val PATH      = "path"
  val OVERWRITE = "overwrite"

  def name: String = "Unload File"
  def parameters: Seq[Parameter] = Seq(
    ArtifactParameter(id = FILE, name = "File", artifactType = ArtifactType.FILE),
    StringParameter(id = PATH, name = "Path"),
    BooleanParameter(id = OVERWRITE, name = "Overwrite Existing", required = false, default = Some(false))
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
    val url = if(path.size <= 0) { new URL(path) } 
              else {
                if(path(0) == '/'){ 
                  new URL("file://"+path) 
                } else if(!path.contains(":/") 
                            && Vizier.config.workingDirectory.isDefined) {
                  new URL("file://"+Vizier.config.workingDirectory()+"/"+path)
                } else {
                  new URL(path)
                }
              }

    url.getProtocol() match {
      case "file" if Vizier.config.serverMode() => {
        context.error("Writing to the local file system is disabled in server mode")
        return
      }
      case "file"  => {
        val source = fileArtifact.absoluteFile.toPath
        val destination = Paths.get(url.getPath)
        if(destination.toFile.exists){
          if(!arguments.getOpt[Boolean](OVERWRITE).getOrElse(false)){
            context.error(s"The file $url already exists.  Check 'Overwrite Existing' if you want to replace it")
            return
          } 
          destination.toFile.delete()
        }
        Files.copy(source, destination)
        context.message(s"Copied $fileName (id = ${fileArtifact.id}) to $destination")
      }
      case _ => {
        context.error(s"Invalid protocol: ${url.getProtocol()}")
        return
      }
    }
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](FILE))
      .andNothingElse
}

