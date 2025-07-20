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

import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.sedona_sql.expressions.raster.RS_AsPNG;
import org.apache.spark.sql.types.DataType
import info.vizierdb.spark.udt.ImageUDT
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.codegen.{CodegenContext, ExprCode}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback

case class SedonaPNGWrapper(png: Expression) extends Expression with CodegenFallback
{
  assert(png.isInstanceOf[RS_AsPNG])

  def dataType: DataType = ImageUDT

  def eval(input: InternalRow): Any = png.eval(input);

  def nullable: Boolean = png.nullable

  def children = Seq(png)

  protected def withNewChildrenInternal(newChildren: IndexedSeq[Expression]) = {
    copy(png = newChildren(0))
  }
}