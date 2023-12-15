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
package info.vizierdb.spark.vizual

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import info.vizierdb.spark.{ DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance }
import info.vizierdb.types._
import info.vizierdb.Vizier
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.apache.spark.sql.types.StructType
import info.vizierdb.catalog.Artifact

case class VizualScriptConstructor(
  script: Seq[VizualCommand],
  input: Option[Identifier],
  schema: Seq[StructField]
)
  extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(context: Identifier => Artifact): DataFrame =
    ExecOnSpark(
      input.map { context(_).dataframeFromContext(context) }
           .getOrElse { Vizier.sparkSession.emptyDataFrame },
      script
    )

  def dependencies = input.toSet
}

object VizualScriptConstructor 
  extends DataFrameConstructorCodec
{
  implicit val format: Format[VizualScriptConstructor] = Json.format
  def apply(j: JsValue) = j.as[VizualScriptConstructor]

  def getSchema(inputSchema: Option[Seq[StructField]], script: Seq[VizualCommand]): Seq[StructField] =
    ExecOnSpark(
      inputSchema.map { sch => 
        Vizier.sparkSession.createDataFrame(new java.util.ArrayList[Row](), StructType(sch)),
      }.getOrElse { Vizier.sparkSession.emptyDataFrame },
      script
    ).schema

}