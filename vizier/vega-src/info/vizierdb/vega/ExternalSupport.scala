package info.vizierdb.vega

import java.net.URL
import play.api.libs.json._

object ExternalSupport
{
  implicit val urlFormat: Format[URL] = Format(
    new Reads[URL] {
      def reads(j: JsValue): JsResult[URL] = JsSuccess(new URL(j.as[String])),
    },
    new Writes[URL] {
      def writes(j: URL): JsValue = JsString(j.toString)
    }
  )

  type Uri = URL
  type UriReference = URL
}