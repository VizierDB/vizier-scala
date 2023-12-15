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
import org.apache.spark.sql.types.StructField
import info.vizierdb.util.StringUtils
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.spark.SparkSchema

case class ExpectedType(field: StructField)
  extends Facet
{
  def identity = ExpectedType.identity
  def description = s"The column ${field.name} should be ${StringUtils.withDefiniteArticle(SparkSchema.friendlyTypeString(field.dataType))}"
  def test(query:DataFrame): Seq[String] =
  {
    query.schema
         .fields
         .find { _.name.equalsIgnoreCase(field.name) }
         // silently pass through missing columns.  Should be caught by ExpectedColumns
         .flatMap { 
           case actual => 
             if( ! field.dataType.equals(actual.dataType) ) { 
               Some(s"${actual.name} is ${StringUtils.withDefiniteArticle(SparkSchema.friendlyTypeString(actual.dataType))} (Expected ${StringUtils.withDefiniteArticle(SparkSchema.friendlyTypeString(field.dataType))})") 
             } else { None }
         }
         .toSeq
  }
  def toJson = Json.toJson(this)
  def affectsColumn = Some(field.name)
}

object ExpectedType
  extends FacetDetector
{
  def identity = "ExpectedType"
  implicit val format: Format[ExpectedType] = Json.format
  def apply(query:DataFrame): Seq[Facet] = 
    query.schema.fields.map { ExpectedType(_) }.toSeq
  def decode(facet: JsValue)(): Facet = facet.as[ExpectedType]
}