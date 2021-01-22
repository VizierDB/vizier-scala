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
package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import org.mimirdb.api.Request
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.api.response.RawJsonResponse
import info.vizierdb.api.handler.DeterministicHandler

object ListTasksHandler
  extends DeterministicHandler
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      RawJsonResponse(
        Json.toJson(
          Scheduler.running
                   .map { workflow => workflow.summarize }
        )
      )
    }
  } 
}

