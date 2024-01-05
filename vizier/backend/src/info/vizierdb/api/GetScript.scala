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
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.catalog.Script
import info.vizierdb.types._

object GetScript
{
  def apply(scriptId: String = null, version: Long = -1): serialized.VizierScript =
  {
    CatalogDB.withDBReadOnly { implicit s =>
      val (script, revision) = Script.getHead(scriptId.toLong)
      revision.describe(script.name)
    }
  }
}

