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