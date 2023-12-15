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
package info.vizierdb.api.websocket

import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.JsValue
import info.vizierdb.delta.WorkflowDelta

case class WebsocketRequest(
  id: Identifier,
  path: Seq[String],
  args: Map[String, JsValue]
)

sealed trait WebsocketResponse
{
  def id: Identifier
}

case class NormalWebsocketResponse(
  id: Identifier,
  response: JsValue
) extends WebsocketResponse

case class ErrorWebsocketResponse(
  id: Identifier,
  message: String,
  detail: Option[String] = None
) extends WebsocketResponse

case class NotificationWebsocketMessage(
  delta: WorkflowDelta
) extends WebsocketResponse { def id = 0 }