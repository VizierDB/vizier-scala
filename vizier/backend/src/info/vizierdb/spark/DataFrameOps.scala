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
package info.vizierdb.spark

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.NamedExpression
import info.vizierdb.VizierException

object DataFrameOps
{
  def columns(df: DataFrame): Seq[Column] = 
    outputs(df).map { new Column(_) }

  def columnsWhere(df: DataFrame)(cond: String => Boolean): Seq[Column] = 
    outputs(df).filter { x => cond(x.name) }
               .map { new Column(_) }

  def outputs(df: DataFrame): Seq[NamedExpression] =
    df.queryExecution.logical.output
  
  def safeColumnLookup(df: DataFrame, col: String): Column =
    safeColumnLookupOpt(df, col).getOrElse { 
      throw new VizierException(s"Expected to find $col in ${df.columns.mkString(", ")}")
    }

  def safeColumnLookupOpt(df: DataFrame, col: String): Option[Column] =
    safeOutputLookupOpt(df, col)
      .map { new Column(_) }

  def safeOutputLookup(df: DataFrame, col: String): NamedExpression =
    safeOutputLookupOpt(df, col).getOrElse { 
      throw new VizierException(s"Expected to find $col in ${df.columns.mkString(", ")}")
    }
    
  def safeOutputLookupOpt(df: DataFrame, col: String): Option[NamedExpression] =
    df.queryExecution.logical.output
      .find { _.name == col }

}
