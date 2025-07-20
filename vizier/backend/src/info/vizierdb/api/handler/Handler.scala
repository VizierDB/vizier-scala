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
package info.vizierdb.api.handler

import scala.collection.mutable.Buffer
import scala.annotation.ClassfileAnnotation
import play.api.libs.json._
import info.vizierdb.util.StringUtils
import info.vizierdb.api.Response

abstract class Handler
{

  def summary: String = 
    StringUtils.camelCaseToHuman(
      this.getClass
          .getSimpleName
          .replace("$", "")
          .replace("Handler", "")
    )
  def details = ""
  def responses: Buffer[HandlerResponse] = Buffer.empty
  def requestBody: Option[Content] = None
  def filePart: Option[String] = None

  def handle(
    pathParameters: Map[String, JsValue], 
    request: ClientConnection 
  ): Response

}

