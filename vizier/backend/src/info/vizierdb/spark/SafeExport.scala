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
package info.vizierdb.spark

import org.apache.spark.sql.types.UserDefinedType
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.sedona_sql.expressions.ST_AsText
import org.apache.spark.sql.Column
import org.mimirdb.caveats.implicits._
import org.apache.spark.sql.functions.base64
import org.apache.spark.sql.types.BinaryType

/**
 * Certain column types (most notably UDTs) are not safe for export through 
 * standard mechanisms...  This class contains operations that safely transform
 * the dataframe's attributes into export-friendly types.
 */
object SafeExport
{
  def csv(df: DataFrame): DataFrame =
  {
    val mapping =
      df.schema.fields.map { column =>
        val base = df(column.name)

        column.dataType match {
          case t:UserDefinedType[_] if t.isInstanceOf[GeometryUDT] =>
            new Column(ST_AsText(Seq(base.expr))) as column.name

          case BinaryType => 
            base64(base) as column.name

          case _ => 
            base
        }

      }
    
    df.select(mapping:_*).stripCaveats
  }
}