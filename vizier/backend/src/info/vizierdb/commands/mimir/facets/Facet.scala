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
package info.vizierdb.commands.mimir.facets

import play.api.libs.json._
import org.apache.spark.sql.DataFrame

trait Facet
{
  def identity: String
  def description: String
  def test(query:DataFrame): Seq[String]
  def toJson: JsValue

  def affectsColumn: Option[String]
}

object Facet
{
  val detectors: Map[String, FacetDetector] =
    Seq[FacetDetector](
      ExpectedColumns,
      ExpectedType,
      Nullable,
      ExpectedValues
    ).map { x => x.identity -> x }.toMap

  def detect(query: DataFrame): Seq[Facet] =
    detectors.values.map { _(query) }.flatten.toSeq

  def encode(facet: Facet): JsValue =
    Json.obj(
      "type"    -> facet.identity,
      "content" -> facet.toJson
    )

  def decode(encoded: JsValue): Facet =
    detectors.get( (encoded \ "type").as[String] )
             .map { _.decode( (encoded \ "content").as[JsValue] ) }
             .getOrElse { throw new IllegalArgumentException(s"Unsupported facet type $encoded") }


  implicit val format: Format[Facet] = Format(
    new Reads[Facet] { def reads(j: JsValue) = JsSuccess(decode(j)) },
    new Writes[Facet] { def writes(j: Facet) = encode(j) }
  )
}