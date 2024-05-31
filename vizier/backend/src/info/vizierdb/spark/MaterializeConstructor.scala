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
package info.vizierdb.spark

import play.api.libs.json._
import java.io.File
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.types.{ DataType, StructField }
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.caveats.AnnotateImplicitHeuristics
import org.mimirdb.caveats.implicits._
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.types._
import info.vizierdb.filestore.Filestore
import info.vizierdb.Vizier
import info.vizierdb.catalog.Artifact
import com.typesafe.scalalogging.LazyLogging

case class MaterializeConstructor(
  input: Identifier,
  schema: Seq[StructField], 
  artifactId: Identifier,
  projectId: Identifier,
  format: String, 
  options: Map[String,String],
)
  extends DataFrameConstructor
  with LazyLogging
{
  def construct(
    context: Identifier => Artifact
  ): DataFrame = 
  {
    logger.info("In Materialize Constructor")
    var parser = Vizier.sparkSession.read.format(format)
    for((option, value) <- options){
      parser = parser.option(option, value)
    }
    val materialized = Filestore.getAbsolute(projectId = projectId, artifactId = artifactId)
    var df = parser.load(materialized.toString)

    // println(absoluteUrl)

    // add a silent projection to "strip out" all of the support metadata.
    df = df.select( schema.map { field => df(field.name) }:_* )
    
    return df
  }

  def provenance(
    context: Identifier => Artifact
  ): DataFrame = 
    context(input)
      .datasetDescriptor
      .constructor
      .provenance(context)

  def dependencies = Set(input)
}

object MaterializeConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[MaterializeConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[MaterializeConstructor]

  val DEFAULT_FORMAT = "parquet"
}