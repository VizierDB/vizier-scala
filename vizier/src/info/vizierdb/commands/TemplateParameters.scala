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
package info.vizierdb.commands

object TemplateParameters
{
  val COLUMN = 
    ColIdParameter(id = "column", name = "Column")

  val DATATYPE: EnumerableParameter = DATATYPE()

  def DATATYPE(id:String = "schema_datatype", required: Boolean = false) =
    EnumerableParameter(name = "Data Type", id = id, required = required, values = EnumerableValue.withNames(
      "String"                 -> "string",
      "Real"                   -> "real",
      "Float"                  -> "float",
      "Double Precision Float" -> "double",
      "Bool"                   -> "boolean",
      "16-bit Integer"         -> "short",
      "32-bit Integer"         -> "int",
      "64-bit Integer"         -> "long",
      "1 Byte"                 -> "byte",
      "Date"                   -> "date",
      "Date+Time"              -> "timestamp",
    ), default = Some(0), aliases = Map(
      "integer" -> "int",
    ))

  val SCHEMA = 
    ListParameter(name = "Schema (leave blank to guess)", id = "schema", required = false, components = Seq(
      StringParameter(name = "Column Name", id = "schema_column", required = false),
      DATATYPE
    )),

}

