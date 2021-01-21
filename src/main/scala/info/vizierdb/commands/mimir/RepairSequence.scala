/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.MissingKeyLensConfig
import org.apache.spark.sql.types.{ ShortType, IntegerType, LongType }
import org.mimirdb.lenses.Lenses

object RepairSequence 
  extends LensCommand
{
  def name = "Repair Sequence"
  def lens = Lenses.missingKey

  def lensParameters: Seq[Parameter] = Seq(
    TemplateParameters.COLUMN,
    IntParameter(id = "low", name = "Low Value (optional)", required = false),
    IntParameter(id = "high", name = "High Value (optional)", required = false),
    IntParameter(id = "step", name = "Step Size (optional)", required = false),
  )

  def lensFormat(arguments: Arguments): String = 
    s"FIX SEQUENCE ON COLUMN ${arguments.get[Int]("column")}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue = 
  {
    val low = arguments.getOpt[Long]("low")
    val high = arguments.getOpt[Long]("high")
    val step = arguments.getOpt[Long]("step")
    val field = schema(arguments.get[Int]("column"))

    field.dataType match {
      case ShortType | IntegerType | LongType => ()
      case _ => 
        throw new IllegalArgumentException(s"The key column should be an integer type and not ${field.dataType.prettyJson}")
    }

    if(low.isDefined && high.isDefined && step.isDefined){
      Json.toJson(MissingKeyLensConfig(key = field.name, t = field.dataType, 
        low = low.get, high = high.get, step = step.get
      ))
    } else {
      JsString(field.name)
    }
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] =
  {
    val arguments = lensArgs.as[MissingKeyLensConfig]
    Map(
      "low"  -> JsNumber(arguments.low),
      "high" -> JsNumber(arguments.high),
      "step" -> JsNumber(arguments.step)
    )
  }
}

