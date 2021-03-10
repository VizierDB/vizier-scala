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
package info.vizierdb.api.handler

import play.api.libs.json._
import scala.reflect._
import scala.reflect.runtime.universe._
import json.{ Schema => JsonSchema }

sealed trait Content

case class JsonContent[T](
  schema: JsValue,
  description: String = "",
)(
  examples: String*
) extends Content

case class HandlerResponse(
  content: Seq[Content] = Seq.empty,
  code: Int = 200,
  description: String = "",
) extends scala.annotation.StaticAnnotation

