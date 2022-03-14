package info.vizierdb.spark.arrow

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import info.vizierdb.util.FeatureSupported
import scala.util.Random
import org.apache.spark.sql.ArrowProxy

object ArrowQuery
{
  def apply(df: DataFrame): ConnectionDetails = 
  {
    FeatureSupported.brokenByJavaVersion("Arrow Dataframes", 9)
    val tempFile = s"./vizierdf_${Random.alphanumeric.take(10).toString}"
    val (port, secret) = ArrowProxy.writeToMemoryFile(tempFile, df)
    ConnectionDetails(port, secret)
  }

  case class ConnectionDetails (
      port: Int,
      secret: String
  )

  object ConnectionDetails {
    implicit val format: Format[ConnectionDetails] = Json.format
  }
}