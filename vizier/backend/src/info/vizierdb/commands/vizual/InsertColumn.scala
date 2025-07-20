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
package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import info.vizierdb.spark.vizual
import info.vizierdb.spark.SparkSchema

object InsertColumn extends VizualCommand
{
  val PARA_POSITION = "position"
  val PARA_NAME = "name"
  val PARA_DATATYPE = "dataType"

  def name: String = "Insert Column"
  def vizualParameters: Seq[Parameter] = Seq(
    IntParameter(id = PARA_POSITION, name = "Position", required = false),
    StringParameter(id = PARA_NAME, name = "Column Name"),
    TemplateParameters.DATATYPE(id = PARA_DATATYPE)
  )
  def format(arguments: Arguments): String = 
    s"INSERT COLUMN ${arguments.get[String](PARA_NAME)} OF TYPE ${arguments.get[String]("datatype")} INTO DATASET ${arguments.get[String](PARA_DATASET)}${arguments.getOpt[Int](PARA_POSITION).map {" AT POSITION "+_}.getOrElse{""}}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.InsertColumn(
        position = arguments.getOpt[Int](PARA_POSITION),
        name = arguments.get[String](PARA_NAME),
        dataType = arguments.getOpt[String](PARA_DATATYPE).map { SparkSchema.decodeType(_) }
      )
    )
  override def hidden: Boolean = true
}

