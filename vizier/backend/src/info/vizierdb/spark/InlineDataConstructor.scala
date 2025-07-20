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
package info.vizierdb.spark

import play.api.libs.json._
import scala.collection.JavaConverters
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ SparkSession, DataFrame, Row }
import org.apache.spark.sql.types._

import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.types._
import info.vizierdb.Vizier
import info.vizierdb.catalog.Artifact

case class InlineDataConstructor(
  schema: Seq[StructField],
  data: Seq[Seq[JsValue]]
) extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(
    context: Identifier => Artifact
  ): DataFrame =
  {
    val types = schema.map { _.dataType }
    val rows:Seq[Row] = 
      data.map { row => 
        Row.fromSeq(row.zip(types).map { case (field, t) => 
          SparkPrimitive.decode(field, t, castStrings = true)
        })
      }
    return Vizier.sparkSession.createDataFrame(
      JavaConverters.seqAsJavaList(rows),
      StructType(schema)
    )
  }

  def dependencies: Set[Identifier] = Set.empty

}


object InlineDataConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[InlineDataConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[InlineDataConstructor]
}
