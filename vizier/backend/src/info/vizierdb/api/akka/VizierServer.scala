package info.vizierdb.api.akka

import play.api.libs.json._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import info.vizierdb.Vizier
import akka.http.scaladsl.model.StatusCodes.MovedPermanently
import java.time.ZonedDateTime
import info.vizierdb.spark.caveats.QueryWithCaveats 
import akka.http.scaladsl.marshalling.Marshaller
import scala.concurrent.Future
import akka.http.scaladsl.model.ContentTypes._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.{ InputStream, FileInputStream, File }
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.Route

object VizierServer
{
  implicit val system = ActorSystem(Behaviors.empty, "server")
  implicit val executionContext = system.executionContext

  def withFile(fieldName: String)( handler: ((InputStream, String)) => Route ): Route =
  {
    def tempDestination(fileInfo: FileInfo): File =
      File.createTempFile(fileInfo.fileName, ".tmp")
    storeUploadedFiles(fieldName, tempDestination){ files => 
      try {
        val (metadata, file) = files.head
        val input = new FileInputStream(file)
        try {
          handler( (input, metadata.fileName) )
        } finally { input.close() }
      } finally {
        files.foreach { _._2.delete() }
      }
    }
  }
  
  lazy val allowAnyConnection = Vizier.config.devel() || Vizier.config.connectFromAnyHost()
  lazy val host = 
      if(allowAnyConnection){ "0.0.0.0" }
      else { "localhost" }
  lazy val port =
    Vizier.config.port()
  var started: ZonedDateTime = null
  val NAME = "vizier-scala/akka"
  val MAX_UPLOAD_SIZE = 1024*1024*100 // 100MB
  val MAX_FILE_MEMORY = 1024*1024*10  // 10MB
  val MAX_DOWNLOAD_ROW_LIMIT = QueryWithCaveats.RESULT_THRESHOLD
  val BACKEND = "SCALA"
  val VERSION = Vizier.config.VERSION

  def publicURL =
    Vizier.config.publicURL.get
          .getOrElse { s"http://$host:$port/" }

  def run()
  {
    val mainServer = 
      Http().newServerAt(host, port).bind(
        concat(
          pathPrefix("vizier-db" / "api" / "v1") {
            AllRoutes.routes
          },
          path(PathEnd) {
            redirect(
              s"${publicURL}index.html",
              MovedPermanently
            )
          },
          getFromResourceDirectory("ui"),
        )
      )

    started = ZonedDateTime.now()
  }
}