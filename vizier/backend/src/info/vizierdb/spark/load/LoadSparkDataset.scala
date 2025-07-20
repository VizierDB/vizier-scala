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
package info.vizierdb.spark.load

import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands.FileArgument
import info.vizierdb.types._
import info.vizierdb.serializers._
import info.vizierdb.spark._
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.apache.spark.sql.DataFrame
import info.vizierdb.Vizier
import org.apache.spark.sql.types.StructType
import info.vizierdb.catalog.Artifact

case class LoadSparkDataset(
  url: FileArgument,
  format: String,
  schema: Seq[StructField],
  sparkOptions: Map[String, String] = Map.empty,
  projectId: Identifier
) extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{
  override def construct(context: Identifier => Artifact): DataFrame = 
  {
    Vizier.sparkSession
          .read
          .format(format)
          .schema(StructType(schema))
          .options(sparkOptions)
          .load(url.getPath(projectId, noRelativePaths = true)._1)
  }

  override def dependencies: Set[Identifier] = Set.empty
}

object LoadSparkDataset
  extends DataFrameConstructorCodec
{
  implicit val format: Format[LoadSparkDataset] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[LoadSparkDataset]

  val LEADING_WHITESPACE = raw"^[ \t\n\r]+"
  val INVALID_LEADING_CHARS = raw"^[^a-zA-Z_]+"
  val INVALID_INNER_CHARS = raw"[^a-zA-Z0-9_]+"

  def cleanColumnName(name: String): String =
    name.replaceAll(LEADING_WHITESPACE, "")
        .replaceAll(INVALID_LEADING_CHARS, "_")
        .replaceAll(INVALID_INNER_CHARS, "_")

  def infer(
    url: FileArgument,
    format: String,
    schema: Option[Seq[StructField]],
    sparkOptions: Map[String, String] = Map.empty,
    projectId: Identifier,
  ): LoadSparkDataset =
  {
    LoadSparkDataset(
      url, 
      format,
      schema.getOrElse {
        Vizier.sparkSession
              .read
              .format(format)
              .options(
                sparkOptions ++ (
                  format match {
                    case "csv" => Map("inferSchema" -> "true")
                    case _ => Map.empty
                  }
                ))
              .load(url.getPath(projectId, noRelativePaths = true)._1)
              .schema
              .map { s => s.copy(name = cleanColumnName(s.name)) }
      },
      sparkOptions,
      projectId
    )
  }
}