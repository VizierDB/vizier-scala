package info.vizierdb.ui.network

import scala.concurrent.Future
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue
import org.scalajs.dom.raw.XMLHttpRequest
import play.api.libs.json.Json
import info.vizierdb.ui.Vizier

trait APIExtras
{
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

}

