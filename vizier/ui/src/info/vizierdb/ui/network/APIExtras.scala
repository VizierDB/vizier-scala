package info.vizierdb.ui.network

import scala.concurrent.Future
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global

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
}

