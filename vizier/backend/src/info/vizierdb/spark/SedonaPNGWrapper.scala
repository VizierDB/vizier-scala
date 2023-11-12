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