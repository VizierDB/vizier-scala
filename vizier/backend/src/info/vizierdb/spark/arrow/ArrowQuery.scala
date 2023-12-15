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
    FeatureSupported.brokenByJavaVersion("Arrow Dataframes", 12)
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