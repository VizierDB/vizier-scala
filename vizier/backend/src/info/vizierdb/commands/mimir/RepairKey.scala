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
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.{ DataFrame, Column }
import info.vizierdb.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.mimirdb.caveats.implicits._

object RepairKey 
  extends LensCommand
{ 
  def COUNT_COLUMN(x: String) = "__MIMIR_REPAIR_KEY_COUNT_"+x
  def COUNT_EXAMPLES(x: String) = "__MIMIR_REPAIR_KEY_EXAMPLES_"+x


  def name: String = "Fix Key Column"
  def lensParameters: Seq[Parameter] = Seq(
    TemplateParameters.COLUMN,
  )
  def format(arguments: Arguments): String = 
    s"REPAIR KEY COLUMN ${arguments.get[Int]("column")}"

  def title(arguments: Arguments): String =
    s"REPAIR KEY ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
    // no-op
    Map.empty

  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    val keyName = df.columns(arguments.get[Int](TemplateParameters.PARAM_COLUMN))
    val keyAttribute = df(keyName)
    val nonKeyAttributes = 
        df.schema.fieldNames
          .filter { !_.equalsIgnoreCase(keyName) }

    val nonKeyCounts:Seq[Column] =
        nonKeyAttributes
          .map { attr => countDistinct(df(attr)) as COUNT_COLUMN(attr) }

    val nonKeyExamples:Seq[Column] =
        nonKeyAttributes
          .map { attr => array_distinct(collect_list(df(attr).cast(StringType))) as COUNT_EXAMPLES(attr) }

    val nonKeyAggregates:Seq[Column] = 
          nonKeyAttributes
            .map { attr => first(df(attr)) as attr } 

    val output = 
      df.groupBy(keyAttribute)
        .agg( nonKeyAggregates.head, (nonKeyAggregates.tail ++ nonKeyCounts ++ nonKeyExamples) :_* )
    
    val outputKeyAttribute = output(keyName)

    val context = arguments.pretty(PARAM_DATASET)

    val outputSchema = 
      df.schema.fieldNames
        .map { 
          case field if field.equalsIgnoreCase(keyName) => output(field)
          case field => {
            output(field).caveatIf(
              concat(
                output(field),
                lit(" could be one of "),
                (output(COUNT_COLUMN(field)) - lit(1)).cast(StringType),
                lit(s" other distinct values for $context.$field when $context.${keyName} = "),
                outputKeyAttribute.cast(StringType),
                lit(", including "),
                concat_ws(", ",
                  slice(
                    filter(
                      output(COUNT_EXAMPLES(field)),
                      (x) => output(field) =!= x
                    ),
                    1, 3 // limit to 3 examples
                  )
                ),
                // we display 4 values.  3 examples + 1 baseline.
                when(output(COUNT_COLUMN(field)) > lit(4), lit(", ... and more"))
                  .otherwise("")
              ),
              output(COUNT_COLUMN(field)) > 1
            ).as(field)
          }
       }
    output.select(outputSchema:_*)
  }
}

