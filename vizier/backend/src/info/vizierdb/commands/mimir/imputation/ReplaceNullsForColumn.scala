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
import org.apache.spark.sql.{ DataFrame, Dataset }
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.functions.{ when, expr }
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.param.Param


class ReplaceNullsForColumn(override val uid: String) 
  extends Transformer 
  with DefaultParamsWritable 
{
  final val inputColumn= new Param[String](this, "inputColumn", "The input column to replace nulls for")
  final val outputColumn= new Param[String](this, "outputColumn", "The output column with replaced nulls")
  final val replacementColumn= new Param[String](this, "replacementColumn", "The column to replace nulls with")
  def setInputColumn(value: String): this.type = set(inputColumn, value)
  def setOutputColumn(value: String): this.type = set(outputColumn, value)
  def setReplacementColumn(value: String): this.type = set(replacementColumn, value)
  def this() = this(Identifiable.randomUID("replacenullsforcolumn"))
  def copy(extra: ParamMap): ReplaceNullsForColumn = defaultCopy(extra)
  override def transformSchema(schema: StructType): StructType = schema
  def transform(df: Dataset[_]): DataFrame = df.withColumn($(outputColumn), when(df($(inputColumn)).isNull.or(df($(inputColumn)).isNaN), expr($(replacementColumn))).otherwise(df($(inputColumn)))  )
}
  
object ReplaceNullsForColumn extends DefaultParamsReadable[ReplaceNullsForColumn] {
  override def load(path: String): ReplaceNullsForColumn = super.load(path)
}