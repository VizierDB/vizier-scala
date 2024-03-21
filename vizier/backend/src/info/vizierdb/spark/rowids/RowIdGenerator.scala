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
package info.vizierdb.spark.rowids

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{ Expression, Generator }
import org.apache.spark.sql.catalyst.expressions.codegen.{ CodegenContext, ExprCode }
import org.apache.spark.sql.types.{ StructType, StructField, LongType }
import org.apache.spark.sql.catalyst.expressions.UserDefinedExpression
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback

case class RowIdGenerator(source: Generator) 
  extends Generator
  with UserDefinedExpression
  with CodegenFallback
{
  def name = "RowIdGenerator"

  // Members declared in org.apache.spark.sql.catalyst.expressions.Generator
  def elementSchema: StructType = 
    StructType(source.elementSchema.fields :+ RowIdGenerator.FIELD)

  protected def withNewChildrenInternal(newChildren: IndexedSeq[Expression]): Expression = 
    RowIdGenerator(newChildren(0).asInstanceOf[Generator])

  override def eval(input: InternalRow): TraversableOnce[InternalRow] = 
    source.eval(input)
          .toIterable
          .zipWithIndex
          .map { case (row, idx) => InternalRow.fromSeq(row.toSeq(source.elementSchema) :+ idx.toLong) }
  
  // Members declared in org.apache.spark.sql.catalyst.trees.TreeNode
  def children: Seq[Expression] = Seq(source)
}

object RowIdGenerator
{
  val ATTRIBUTE = AnnotateWithRowIds.ATTRIBUTE + "_GEN_INDEX"
  val FIELD = StructField(ATTRIBUTE, LongType)
}