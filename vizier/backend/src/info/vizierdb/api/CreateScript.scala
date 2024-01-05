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
package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.catalog.Project
import info.vizierdb.api.response._
import info.vizierdb.api.response.RawJsonResponse
import info.vizierdb.api.handler.DeterministicHandler
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.catalog.Script
import info.vizierdb.catalog.ScriptRevision
import info.vizierdb.types._
import info.vizierdb.VizierException

object CreateScript
{
  def apply(
    modules: Seq[serialized.VizierScriptModule],
    projectId: String,
    branchId: String,
    workflowId: String,
    name: String = null,
    scriptId: String = null, 
  ): serialized.VizierScript =
  {
    CatalogDB.withDB { implicit s => 
      val script: Script = 
        (
          Option(scriptId),
          Option(name), 
        ) match {
          case (Some(id), _) => Script.get(target = id.toLong)
          case (_, Some(name)) => Script.make(name = name)
          case (None, None) => throw new IllegalArgumentException("Need a name or a script ID")
        }

      script.modify(
        ScriptRevision(
          scriptId = -1,
          version = -1,
          projectId = projectId.toLong,
          branchId = branchId.toLong,
          workflowId = workflowId.toLong,
          modules = Json.toJson(modules).toString.getBytes
        )
      )._2.describe(name = name)
    }
  }
}

