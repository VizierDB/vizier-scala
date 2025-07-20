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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema
import info.vizierdb.spark.SparkPrimitive.dataTypeFormat
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.{ DataType, StringType }
import org.mimirdb.lenses.inference.InferTypes

object TypeInference
  extends LensCommand
{ 
  val PARAM_SCHEMA = "schema"
  val PARAM_COLUMN = "schema_column"
  val PARAM_DATATYPE = TemplateParameters.PARAM_DATATYPE

  val name: String = "Assign Datatypes"

  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_SCHEMA, name = "Schema (leave blank to guess)", required = false, components = Seq(
      ColIdParameter(id = PARAM_COLUMN, name = "Column Name", required = false),
      TemplateParameters.DATATYPE
    )),
  )
  def format(arguments: Arguments): String = 
    // "CAST TYPES"
    s"CAST TYPES TO (${arguments.getList(PARAM_SCHEMA).map { col => 
      s"${col.get[Int](PARAM_COLUMN)} ${col.get[String](PARAM_DATATYPE)}"
    }.mkString(", ")})"

  def title(arguments: Arguments): String =
    s"ASSIGN TYPES ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {
    var targets = arguments.getList(PARAM_SCHEMA).map { col =>
                    StructField(
                      df.columns(col.get[Int](PARAM_COLUMN)),
                      SparkSchema.decodeType(col.get[String](PARAM_DATATYPE))
                    )
                  }
    
    if(!targets.isEmpty) { return Map.empty }
    context.message("No target columns specified.  Taking all string columns.")

    targets = df.schema.filter { _.dataType == StringType }

    val inferred = 
      InferTypes(df, attributes = targets.map { _.name }.toSeq)
        .map { field => field.name -> SparkSchema.encodeType(field.dataType) }
        .toMap
    Map(
      PARAM_SCHEMA -> Seq(
        df.schema.zipWithIndex.map { case (col, idx) => 
          Map(
            PARAM_COLUMN -> idx, 
            PARAM_DATATYPE -> inferred.getOrElse(col.name, SparkSchema.encodeType(col.dataType))
          )
        }
      )
    )
  }
  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    val targets = arguments.getList(PARAM_SCHEMA).map { col =>
                    df.columns(col.get[Int](PARAM_COLUMN)) -> 
                      SparkSchema.decodeType(col.get[String](PARAM_DATATYPE))
                  }
                  .toMap

    val columns = df.columns
                    .map { 
                      case col if targets contains col => 
                        df(col).cast(targets(col))
                      case col => 
                        df(col)
                    }

    df.select(columns:_*)
  }
}

