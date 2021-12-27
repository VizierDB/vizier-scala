package info.vizierdb.spark.rowids

import org.apache.spark.sql.types._
import org.apache.spark.sql.catalyst.expressions._

object MergeRowIds
{
  def apply(exprs:Expression*): Expression = 
    exprs match {
      case Seq() => Literal(1l)
      case Seq(singleton) => singleton.dataType match {
        case n:NumericType => Cast(singleton, LongType)
        case _ => Cast(new Murmur3Hash(exprs), LongType)
      }
      case _ => Cast(new Murmur3Hash(exprs), LongType)
    }

  def apply(name: String, id: ExprId, exprs: Expression*): NamedExpression = 
    Alias(apply(exprs:_*), name)(id)
}