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
package info.vizierdb.api.akka

import play.api.libs.json._
import akka.actor.ActorSystem
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
import info.vizierdb.api.Response
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import info.vizierdb.util.OutputStreamIterator
import info.vizierdb.VizierException
import info.vizierdb.api.websocket.BranchWatcherSocket
import info.vizierdb.api.spreadsheet.SpreadsheetSocket
import scala.collection.immutable
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import java.util.concurrent.Executors
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import akka.stream.Materializer
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.server.ExceptionHandler
import info.vizierdb.api.response.ErrorResponse
import org.apache.http
import com.typesafe.scalalogging.LazyLogging

object VizierServer
  extends LazyLogging
{
  implicit val system = ActorSystem("vizier")
  implicit val executionContext: ExecutionContextExecutor = 
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))

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
  val DEFAULT_DISPLAY_ROWS = 20
  val MAX_DOWNLOAD_ROW_LIMIT = QueryWithCaveats.RESULT_THRESHOLD
  val BACKEND = "SCALA"
  val VERSION = Vizier.config.VERSION

  def publicURL =
    Vizier.config.publicURL.get
          .getOrElse { s"http://$host:$port/" }

  val exceptionHandler = ExceptionHandler {
    case ErrorResponse(response) =>
      complete(
        HttpResponse(
          StatusCodes.UnprocessableEntity,
          entity = HttpEntity.Strict(
            response.contentType,
            ByteString(response.getBytes)
          ),
        )
      )
  }

  def run()
  {
    val mainServer = 
      Http().newServerAt(host, port).bind(
        // Most modern web browsers will use the CORS protocol to test whether
        // the server will accept a request before actually issuing the request.
        // https://en.wikipedia.org/wiki/Cross-origin_resource_sharing
        // If the server doesn't respond to the CORS request, the client will 
        // fail the base request.  akka-http-cors will generate these responses
        // automatically.
        cors() {
          handleExceptions(exceptionHandler) {
            redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
              concat(

                // API requests
                pathPrefix("vizier-db" / "api" / "v1") {

                  // Websockets
                  concat(
                    path("websocket") {
                      extractClientIP { ip => 
                        println("Websocket!")
                        handleWebSocketMessages(BranchWatcherSocket.monitor(ip.toString))
                      }
                    },
                    path("spreadsheet") {
                      extractClientIP { ip => 
                        println("Spreadsheet!")
                        handleWebSocketMessages(SpreadsheetSocket.monitor(ip.toString))
                      }
                    },

                    // All the other API routes
                    AllRoutes.routes
                  )
                },

                // Swagger requests
                path("swagger") { 
                  redirect(
                    s"swagger/index.html",
                    MovedPermanently
                  )
                },
                path("swagger/") { 
                  redirect(
                    s"swagger/index.html",
                    MovedPermanently
                  )
                },
                pathPrefix("swagger") {
                  getFromResourceDirectory("swagger")
                },

                // Requests for the root should go to index.html
                path(PathEnd) {
                  redirect(
                    s"${publicURL}index.html",
                    MovedPermanently
                  )
                },

                // Requests for the root should go to index.html
                path("projects" / LongNumber) { (projectId) =>
                  redirect(
                    s"${publicURL}project.html?projectId=${projectId}",
                    MovedPermanently
                  )
                },

                // Raw file requests get directed to the ui directory
                getFromResourceDirectory("ui"),
              )
            }
          }
        }
      )

    started = ZonedDateTime.now()
  }

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

  object RouteImplicits 
  {
    implicit def jsonResponseToAkkaResponse[T](json: T)(implicit writes: Writes[T]): Route =
    {
      complete(json)
    }

    implicit def vizierResponseToAkkaResponse(vizierResp: Response): Route =
    {
      val responseEntity =
        if(vizierResp.contentLength > 0){ 
          HttpEntity.Default(
            contentType = vizierResp.contentType,
            contentLength = vizierResp.contentLength,
            data = Source.fromIterator { () =>
              val buffer = new OutputStreamIterator()
              executionContext.execute(
                new Runnable {
                  def run() =
                  {
                    try {
                      vizierResp.write(buffer)
                      buffer.close()
                    } catch {
                      case t: Throwable => 
                        logger.error(s"Error writing Vizier response to Akka: $t")
                    }
                  }
                }
              )
              buffer.iterator
            }
          )
        } else {
          HttpEntity.empty(vizierResp.contentType)
        }

      complete(
        status = vizierResp.status,
        headers = immutable.Seq.empty ++ (
          vizierResp.headers
                    .map { case (h, v) => HttpHeader.parse(h, v) match {
                                                      case HttpHeader.ParsingResult.Ok(h, _) => h
                                                      case err => 
                                                        throw new VizierException(s"Internal error: Can't parse header $h: $v\n${err.errors.mkString("\n")}")
                                                    } }
        ),
        responseEntity
      )
    }

    implicit def optionIfNeeded[T](v: T): Option[T] = Some(v)
  }

  implicit val stringRequestUnmarshaller = new Unmarshaller[HttpRequest, String] {
    override def apply(
      request: HttpRequest
    )(implicit ec: ExecutionContext, m: Materializer): Future[String] =
      Unmarshaller.stringUnmarshaller(request.entity)
  }
}