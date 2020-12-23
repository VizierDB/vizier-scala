package info.vizierdb.export

import play.api.libs.json._
import java.time.{ LocalDateTime, ZonedDateTime, ZoneId }
import java.time.format.DateTimeParseException

case class ExportedTimestamps(
  createdAt: ZonedDateTime,
  startedAt: Option[ZonedDateTime],
  finishedAt: Option[ZonedDateTime],
  lastModifiedAt: Option[ZonedDateTime]
)

object ExportedTimestamps
{
  def parseDate(dateStr: String): ZonedDateTime =
  {
    try { 
      ZonedDateTime.parse(dateStr)
    } catch {
      case _:DateTimeParseException => 
        ZonedDateTime.of(
          LocalDateTime.parse(dateStr),
          ZoneId.systemDefault()
        )
    }
  }

  implicit val format = Format[ExportedTimestamps](
    new Reads[ExportedTimestamps]{
      def reads(j: JsValue): JsResult[ExportedTimestamps] = 
        JsSuccess(
          ExportedTimestamps(
            createdAt = parseDate( (j \ "createdAt").as[String] ),
            startedAt = (j \ "startedAt").asOpt[String].map { parseDate(_) },
            finishedAt = (j \ "startedAt").asOpt[String].map { parseDate(_) },
            lastModifiedAt = (j \ "lastModifiedAt").asOpt[String].map { parseDate(_) },
          )
        )
    },
    Json.writes
  )
}