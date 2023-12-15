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
package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.catalyst.analysis.UnresolvedException

case class RValueExpression(rvalue: RValue)
  extends Expression with Unevaluable
{
  def children: Seq[Expression] = Seq.empty

  protected def withNewChildrenInternal(newChildren: IndexedSeq[Expression]): Expression = this

  def nullable: Boolean = true

  def dataType: DataType = 
    throw new UnresolvedException("RValueExpression")

  override def toString: String = 
    rvalue.toString()

  override def sql: String = 
    rvalue.column.toString()
}

case class InvalidRValue(msg: String)
  extends Expression with Unevaluable
{
  def children: Seq[Expression] = Seq.empty

  protected def withNewChildrenInternal(newChildren: IndexedSeq[Expression]): Expression = this

  def nullable: Boolean = true

  def dataType: DataType = 
    throw new UnresolvedException("InvalidRValue")  

  override def toString: String = 
    s"[ERROR: $msg]"
}