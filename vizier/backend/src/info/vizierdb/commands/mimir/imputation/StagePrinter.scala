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
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.param.Param
import org.apache.spark.sql.{ DataFrame, Dataset }
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.functions.{ col }

class StagePrinter(val sname:String, override val uid: String) 
  extends Transformer 
  with DefaultParamsWritable {
  final val stageName = new Param[String](this, "stageName", "The stage name to print")
  def this(stageName:String) = {
    this(stageName, Identifiable.randomUID("stageprinter"))
    this.setStageName(stageName)
  }
  def this() = this("", Identifiable.randomUID("stageprinter"))
  def setStageName(value: String): this.type = set(stageName, value)
  def copy(extra: ParamMap): StagePrinter = defaultCopy(extra)
  override def transformSchema(schema: StructType): StructType = schema
  def transform(df: Dataset[_]): DataFrame = {
    //println(s"\n------------------pipeline stage: ${$(stageName)}: \n${df.schema.fields.mkString("\n")}\n-----------------------------\n")
    //df.show()
    df.select(col("*"))
  }
}
  
object StagePrinter extends DefaultParamsReadable[StagePrinter] {
  override def load(path: String): StagePrinter = super.load(path)
}