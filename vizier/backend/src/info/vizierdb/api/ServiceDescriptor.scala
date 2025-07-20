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
package info.vizierdb.api

import java.time.format.DateTimeFormatter
import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import info.vizierdb.api.akka.VizierServer
import info.vizierdb.commands.Commands
import info.vizierdb.api.response.RawJsonResponse
import info.vizierdb.api.handler.DeterministicHandler
import info.vizierdb.serializers._
import info.vizierdb.serialized

object ServiceDescriptor
{
  def apply(): serialized.ServiceDescriptor =
    serialized.ServiceDescriptor(
      name = VizierServer.NAME,
      startedAt = VizierServer.started,
      defaults = serialized.ServiceDescriptorDefaults(
        maxFileSize = VizierServer.MAX_UPLOAD_SIZE,
        maxDownloadRowLimit = VizierServer.MAX_DOWNLOAD_ROW_LIMIT
      ),
      environment = serialized.ServiceDescriptorEnvironment(
        name = VizierServer.NAME,
        version = VizierServer.VERSION,
        backend = VizierServer.BACKEND,
        packages = Commands.describe
      ),
    )
}

