package info.vizierdb.api

import java.io.OutputStream
import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging

abstract class Response 
{
  def status: Int
  def contentType: String
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
    output.addHeader("Content-Type", contentType)
    write(output.getOutputStream())
  }
}

abstract class BytesResponse
  extends Response
{
  def status = HttpServletResponse.SC_OK
  def contentType = "text/plain"
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
  override def contentType = "application/json"
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

