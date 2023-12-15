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
package info.vizierdb.commands.markdown

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.ProvenancePrediction

object Markdown extends Command
  with LazyLogging
{
  def PAR_SOURCE = "source"

  def name: String = "Markdown Doc"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = PAR_SOURCE, language = "markdown", name = "Markdown Code"),
  )
  def format(arguments: Arguments): String = 
    s"MARKDOWN"
  def title(arguments: Arguments): String = 
    s"MARKDOWN"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    context.message(MIME.MARKDOWN, arguments.get[String](PAR_SOURCE))
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction.empty

}

