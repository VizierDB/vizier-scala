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

import java.io.OutputStream
import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging
import _root_.akka.http.scaladsl.model.ContentType
import _root_.akka.http.scaladsl.model.ContentTypes

abstract class Response 
{
  def status: Int
  def contentType: ContentType
  def headers: Seq[(String, String)]
  def contentLength: Int

  def write(output: OutputStream):Unit
  def write(output: HttpServletResponse):Unit =
  {
    output.setStatus(status)
    output.setContentLength(contentLength)
    for((header, value) <- headers){
      output.addHeader(header, value)
    }
    output.addHeader("Content-Type", contentType.toString)
    write(output.getOutputStream())
  }
}

abstract class BytesResponse
  extends Response
{
  def status = HttpServletResponse.SC_OK
  def contentType:ContentType = ContentTypes.`text/plain(UTF-8)`
  def getBytes: Array[Byte]

  lazy val byteBuffer = getBytes
  def contentLength: Int = byteBuffer.size

  def write(os: OutputStream)
  {
    os.write(byteBuffer)
    os.flush()
    os.close() 
  }
  def headers = Seq.empty
}

abstract class JsonResponse[R](implicit format: Format[R])
  extends BytesResponse
  with LazyLogging
{
  override def contentType = ContentTypes.`application/json`
  def getBytes = {
    val r = Json.stringify(json)
    logger.trace(s"RESPONSE: $r")
    r.getBytes
  }
  def json = 
    Json.toJson(this.asInstanceOf[R])
}

case class SuccessResponse(message: String) extends JsonResponse[SuccessResponse] {}

object SuccessResponse
{
  implicit val format: Format[SuccessResponse] = Json.format
}


case class LensList (
    lensTypes: Seq[String]
) extends JsonResponse[LensList]

object LensList {
  implicit val format: Format[LensList] = Json.format
}

case class CreateResponse (
            /* name of resulting table */
                  name: String,
            /* schema of resulting table */
                  schema: Seq[StructField],
            /* properties assocated with the resulting table */
                  properties: Map[String, JsValue]
) extends JsonResponse[CreateResponse]

object CreateResponse {
  implicit val format: Format[CreateResponse] = Json.format
}

