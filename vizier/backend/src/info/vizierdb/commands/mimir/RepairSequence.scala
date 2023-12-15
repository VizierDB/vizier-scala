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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.{ ShortType, IntegerType, LongType }
import org.apache.spark.sql.{ DataFrame, Row }
import org.apache.spark.sql.functions._
import org.mimirdb.caveats.implicits._

object RepairSequence 
  extends LensCommand
{
  val PARAM_LOW = "low"
  val PARAM_HIGH = "high"
  val PARAM_STEP = "step"

  def name = "Repair Sequence"

  def lensParameters: Seq[Parameter] = Seq(
    TemplateParameters.COLUMN,
    IntParameter(id = PARAM_LOW, name = "Low Value (optional)", required = false),
    IntParameter(id = PARAM_HIGH, name = "High Value (optional)", required = false),
    IntParameter(id = PARAM_STEP, name = "Step Size (optional)", required = false),
  )

  def format(arguments: Arguments): String = 
    s"FIX SEQUENCE ON COLUMN ${arguments.get[Int]("column")}"

  def title(arguments: Arguments): String =
    s"FIX SEQUENCE ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {
    val field = df.schema(arguments.get[Int](TemplateParameters.PARAM_COLUMN))
    val t = field.dataType

    val stats = df.select(
      min(df(field.name).cast("long")),
      max(df(field.name).cast("long")),
      lit(1l)
    ).collect()(0)

    Map(
      PARAM_LOW -> stats.getAs[Long](0),
      PARAM_HIGH -> (stats.getAs[Long](1)+1l), // Add 1 to make the high value exclusive
      PARAM_STEP -> stats.getAs[Long](2),
    )
  }
  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    val key = df.columns(arguments.get[Int](TemplateParameters.PARAM_COLUMN))

    val range = df.queryExecution
                  .sparkSession
                  .range(
                    arguments.get[Long](PARAM_LOW),
                    arguments.get[Long](PARAM_HIGH),
                    arguments.get[Long](PARAM_STEP)
                  )
    val idField = range("id")
    val fieldRefs = 
      df.schema
        .fieldNames
        .map { field =>
          // Since the key field may be missing values, use the "id" field
          // from the range as a canonical version of it.
          if(field.equalsIgnoreCase(key)) { idField.as(field) }
          // All other fields get pushed through untouched (though we)
          // drop the "_2" from the join.
          else { df(field).as(field) }
        }

    // A left-outer join enforces the presence of all key values.
    range.join(
        df,
        range("id") === df(key),
        "left_outer"
      )
    // Register a caveat for any record that doesn't have a match
      .caveatIf(
        concat(lit("A missing key ("), idField, lit(s") in ${arguments.pretty(PARAM_DATASET)} was added")),
        df(key).isNull
      )
    // And project out the new "id" field.
      .select(fieldRefs:_*)
  }

}

