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

import java.time.format.DateTimeFormatter
import play.api.libs.json._
import org.mimirdb.api.{ Request, JsonResponse, Response }
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import info.vizierdb.VizierAPI
import info.vizierdb.util.HATEOAS
import info.vizierdb.commands.Commands
import info.vizierdb.api.response.RawJsonResponse
import info.vizierdb.api.handler.DeterministicHandler

object ServiceDescriptor
{
  def apply() =
  {
    RawJsonResponse(
      Json.obj(
        "name" -> JsString(VizierAPI.NAME),
        "startedAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(VizierAPI.started),
        "defaults" -> Json.obj(
          "maxFileSize" -> VizierAPI.MAX_UPLOAD_SIZE,
          "maxDownloadRowLimit" -> VizierAPI.MAX_DOWNLOAD_ROW_LIMIT
        ),
        "environment" -> Json.obj(
          "name" -> VizierAPI.SERVICE_NAME,
          "version" -> VizierAPI.VERSION,
          "backend" -> VizierAPI.BACKEND,
          "packages" -> Commands.toJson
        ),
        HATEOAS.LINKS -> HATEOAS(
          HATEOAS.SELF           -> VizierAPI.urls.serviceDescriptor,
          HATEOAS.API_DOC        -> VizierAPI.urls.apiDoc,
          HATEOAS.PROJECT_CREATE -> VizierAPI.urls.createProject,
          HATEOAS.PROJECT_LIST   -> VizierAPI.urls.listProjects,
          HATEOAS.PROJECT_IMPORT -> VizierAPI.urls.importProject,
        )
      )
    )
  }
}

