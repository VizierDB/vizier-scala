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
package info.vizierdb.commands.mimir.imputation

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}
import org.apache.spark.ml.param.Param
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ DataFrame, Row, Dataset }

class CastForStringIndex(override val uid: String) 
  extends Transformer 
  with DefaultParamsWritable {
  final val inputCol= new Param[String](this, "inputCol", "The input column")
  final val outputCol = new Param[String](this, "outputCol", "The output column")
  def setInputCol(value: String): this.type = set(inputCol, value)
  def setOutputCol(value: String): this.type = set(outputCol, value)
  def this() = this(Identifiable.randomUID("castforstringindex"))
  def copy(extra: ParamMap): CastForStringIndex = defaultCopy(extra)
  override def transformSchema(schema: StructType): StructType = StructType(schema.fields.map(sfld => {
    if(sfld.name.equals($(inputCol)))
      sfld.copy(dataType = StringType)
    else sfld
  }))
  def transform(df: Dataset[_]): DataFrame = df.withColumn($(outputCol), df($(inputCol)).cast(StringType))
}
  
object CastForStringIndex extends DefaultParamsReadable[CastForStringIndex] {
  override def load(path: String): CastForStringIndex = super.load(path)
}