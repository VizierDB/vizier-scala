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
import info.vizierdb.types._
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.DataFrame
import info.vizierdb.catalog.Artifact
import info.vizierdb.Vizier

case class RangeConstructor(
	start: Long = 0l, 
	end: Long, 
	step: Long = 1l
)
	extends DataFrameConstructor
	with DefaultProvenance
{
	def dependencies = Set[Identifier]()
	def schema: Seq[StructField] = Seq( StructField("id", LongType) )
	def construct(context: Identifier => Artifact): DataFrame = 
		Vizier.sparkSession.range(start, end, step).toDF
}

object RangeConstructor
	extends DataFrameConstructorCodec
{
  implicit val format: Format[RangeConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[RangeConstructor]
}
