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
package info.vizierdb.ui.network

import scala.concurrent.Future
import info.vizierdb.serialized
import play.api.libs.json.JsValue
import org.scalajs.dom.raw.XMLHttpRequest
import play.api.libs.json.Json
import info.vizierdb.ui.Vizier
import info.vizierdb.util.Cached

trait APIExtras
{
  implicit val ec: scala.concurrent.ExecutionContext

  def serviceDescriptor():Future[serialized.ServiceDescriptor]

  def makeUrl(path: String, query: (String, Option[String])*): String

  def packages(): Future[Seq[serialized.PackageDescription]] =
    serviceDescriptor.map { 
      _.environment
       .packages
       .toSeq
    }

  def websocket =
    makeUrl("/websocket").replaceAll("http://", "ws://")
                         .replaceAll("https://", "wss://")

  def spreadsheet =
    makeUrl("/spreadsheet").replaceAll("http://", "ws://")
                           .replaceAll("https://", "wss://")

  def checkError(resp: XMLHttpRequest): XMLHttpRequest =
  {
    if(resp.status == 200){ 
      resp 
    } else if(resp.responseType == "application/json" || resp.responseType == "") {
      val js = Json.parse(resp.responseText)
      (js \ "message").asOpt[String] match {
        case Some(msg) => Vizier.error(msg)
        case None => Vizier.error(s"Unknown error while handling message to server: Status code ${resp.status}")
      }
    } else {
      Vizier.error(s"Unknown error while handling message to server: Status code ${resp.status} / Response type ${resp.responseType}")
    }
  }

  def configListPythonEnvs(): Future[serialized.PythonSettingsSummary]

  val pythonEnvironments = 
    new Cached[Future[Seq[serialized.PythonEnvironmentSummary]]](
      () => configListPythonEnvs().map { _.environments }
    )

}

