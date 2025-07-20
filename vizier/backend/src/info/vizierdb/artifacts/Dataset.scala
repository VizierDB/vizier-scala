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
package info.vizierdb.artifacts

import play.api.libs.json._
import info.vizierdb.spark.{
  DataFrameConstructor,
  DataFrameConstructorCodec,
  SparkSchema,
}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat

import info.vizierdb.types._
import info.vizierdb.catalog.Artifact

case class Dataset(
  deserializer: String,
  parameters: JsObject,
  properties: Map[String, JsValue]
)
{
  def constructor =
  {
    val deserializerClass = 
      Class.forName(deserializer)
    val deserializerInstance: DataFrameConstructorCodec = 
      deserializerClass
           .getField("MODULE$")
           .get(deserializerClass)
           .asInstanceOf[DataFrameConstructorCodec]
    deserializerInstance(parameters)
  }

  def construct(context: Identifier => Artifact) =
    constructor.construct(context)

  def provenance(context: Identifier => Artifact) =
    constructor.provenance(context)

  def withProperty(prop: (String, JsValue)*): Dataset =
    copy(
      properties = properties ++ prop.toMap
    )

  def schema = 
    constructor.schema

  def transitiveDependencies(
    discovered: Map[Identifier, Artifact], 
    ctx: Identifier => Artifact
  ): Map[Identifier, Artifact] = 
  {
    val newDeps = 
      constructor.dependencies
                 .filterNot { discovered contains _ }
                 .map { x => x -> ctx(x) }
                 .toMap
    newDeps.values
           .foldRight(discovered ++ newDeps) { (dep, accum) =>
              dep match {
                case ds if ds.t == ArtifactType.DATASET => 
                  ds.datasetDescriptor
                    .transitiveDependencies(accum, ctx)
                case _ => accum
              }
            }
  }


}

object Dataset
{
  implicit val format: Format[Dataset] = Format(
    new Reads[Dataset] {
      def reads(j: JsValue): JsResult[Dataset] = 
      {
        JsSuccess(new Dataset(
          deserializer = (j \ "deserializer").asOpt[String]
            .getOrElse { return JsError(Seq(JsPath \ "deserializer" -> Seq())) },
          parameters = (j \ "parameters").asOpt[JsObject]
            .getOrElse { return JsError(Seq(JsPath \ "parameters" -> Seq())) },
          properties = (j \ "properties").asOpt[Map[String, JsValue]]
            .getOrElse { Map.empty },
        ))
      }
    },
    Json.writes[Dataset]
  )

  def apply[T <: DataFrameConstructor](
    constructor: T,
    properties: Map[String, JsValue] = Map.empty
  )(implicit writes: Writes[T]): Dataset =
  {
    Dataset(
      constructor.deserializer,
      Json.toJson(constructor).as[JsObject],
      properties
    )
  }

}