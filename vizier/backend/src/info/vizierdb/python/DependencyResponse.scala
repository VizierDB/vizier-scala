package info.vizierdb.python

import play.api.libs.json._

case class DependencyResponse(
    dependencies: Seq[String],
    // dependencies: Array[String],
    writes: Map[String, Int]
) 

object DependencyResponse {
    implicit val format: Format[DependencyResponse] = Json.format
}
