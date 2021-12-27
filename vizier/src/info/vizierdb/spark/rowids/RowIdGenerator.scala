package info.vizierdb.spark.rowids

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{ Expression, Generator }
import org.apache.spark.sql.catalyst.expressions.codegen.{ CodegenContext, ExprCode }
import org.apache.spark.sql.types.{ StructType, StructField, LongType }

case class RowIdGenerator(source: Generator) extends Generator
{
  // Members declared in org.apache.spark.sql.catalyst.expressions.Expression
  protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = 
    throw new UnsupportedOperationException(s"Cannot generate code for expression: $this")

  // Members declared in org.apache.spark.sql.catalyst.expressions.Generator
  def elementSchema: StructType = 
    StructType(source.elementSchema.fields :+ RowIdGenerator.FIELD)

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