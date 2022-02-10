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
  val PARAM_DATATYPE = "schema_datatype"
  val PARAM_SCHEMA = "schema"
  val PARAM_SCHEMA_COLUMN = "schema_column"
  val PARAM_SCHEMA_TYPE = PARAM_DATATYPE
  val PARAM_COLUMN = "column"

  val COLUMN = 
    ColIdParameter(id = PARAM_COLUMN, name = "Column")

  val DATATYPE: DataTypeParameter = DATATYPE()

  def DATATYPE(id:String = PARAM_DATATYPE, required: Boolean = false, allowOther: Boolean = false) =
    DataTypeParameter(id = id, name = "Data Type", required = required, hidden = false)
    // EnumerableParameter(name = "Data Type", id = id, required = required, values = EnumerableValue.withNames(
    //   "String"                 -> "string",
    //   "Real"                   -> "real",
    //   "Float"                  -> "float",
    //   "Double Precision Float" -> "double",
    //   "Bool"                   -> "boolean",
    //   "16-bit Integer"         -> "short",
    //   "32-bit Integer"         -> "int",
    //   "64-bit Integer"         -> "long",
    //   "1 Byte"                 -> "byte",
    //   "Date"                   -> "date",
    //   "Date+Time"              -> "timestamp",
    // ), default = Some(0), allowOther = allowOther, aliases = Map(
    //   "integer" -> "int",
    // ))

  val SCHEMA = 
    ListParameter(name = "Schema (leave blank to guess)", id = PARAM_SCHEMA, required = false, components = Seq(
      StringParameter(name = "Column Name", id = PARAM_SCHEMA_COLUMN, required = false),
      DATATYPE
    ))

}
