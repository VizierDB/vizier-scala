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
package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException
import org.mimirdb.api.request.QueryMimirRequest
import org.mimirdb.api.request.CreateViewRequest

object EmptyDataset extends Command
{
  def name: String = "Empty Dataset"
  def parameters: Seq[Parameter] = Seq(
    StringParameter(id = "name", name = "Name of Dataset")
  )
  def format(arguments: Arguments): String = 
    s"CREATE EMPTY DATASET ${arguments.pretty("name")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val (dsName, dsId) = context.outputDataset(arguments.get[String]("name"))
    CreateViewRequest(
      input = Map.empty,
      functions = None,
      query = "SELECT '' AS unnamed_column",
      resultName = Some(dsName),
      properties = None
    ).handle
    context.message("Empty Dataset Created")
  }
}

