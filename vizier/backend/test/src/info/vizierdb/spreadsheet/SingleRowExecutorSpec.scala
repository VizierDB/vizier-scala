package info.vizierdb.spreadsheet

import org.specs2.mutable.Specification
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.apache.spark.sql.functions.{ expr => parse, lit }
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import java.util.concurrent.TimeUnit

class SingleRowExecutorSpec
  extends Specification
{
    
  val A = ColumnRef(1, label = "A")
  val B = ColumnRef(2, label = "B")
  val C = ColumnRef(3, label = "C")

  class ExecutorExtensions(exec: SingleRowExecutor)
  {
    def get[T](col: ColumnRef, row: Long): T =
      Await.result(exec.getFuture(col, row), Duration(10, TimeUnit.SECONDS))
           .asInstanceOf[T]

    def get[T](cell: SingleCell): T =
      get(cell.column, cell.row)

    def update(target: LValue, expr: String): Unit =
      exec.update(target, 
        parse(expr).expr
          .transform { 
            case UnresolvedAttribute(Seq(attr)) => 
              exec.columns.keys
                  .find { _.label.toLowerCase == attr.toLowerCase }
                  .map { column => 
                    RValueExpression(OffsetCell(column, 0))
                  }
                  .getOrElse {
                    InvalidRValue(s"Unknown column: '$attr'")
                  }
          }
      )
  }
  implicit def extendExecutor(exec: SingleRowExecutor): ExecutorExtensions =
    new ExecutorExtensions(exec)

  def init(
    data: Map[ColumnRef, Seq[Any]],
    subscribe: RangeSet = null,
  ): (SingleRowExecutor) = 
  {
    val exec = new SingleRowExecutor(
      { (col, row) => 
        Future.successful(data(col)(row.toInt)) },
      { (_, _) => () }
    )


    data.keys.foreach { exec.addColumn(_) }

    exec.subscribe(
      Option(subscribe).getOrElse {
        RangeSet(0, 
                 data.values.map { _.size }.min-1)
      }
    )

    return exec
  }

  "Passthrough" >>
  {
    val exec = init(
      Map( A -> Seq(1, 2, 3, 4) )
    )

    exec.get[Int](A, 0) must beEqualTo(1)
    exec.get[Int](A, 1) must beEqualTo(2)
    exec.get[Int](A, 2) must beEqualTo(3)
    exec.get[Int](A, 3) must beEqualTo(4)
  }

  "Literals" >>
  {
    val exec = init(
      Map( A -> Seq(1, 2, 3, 4) )
    )

    exec.update(A(0), "10")

    val update = 
      exec.updates(A)._1(0).map { _.expression }
    update must beSome { haveClass[Literal] }

    exec.get[Int](A(0)) must beEqualTo(10)
  }

  "Simple References" >> 
  {
    val exec = init(
      Map( 
        A -> Seq(1, 2, 3, 4),
        B -> Seq(5, 6, 7, 8),
      )
    )

    exec.get[Int](B(0)) must beEqualTo(5)
    exec.update(B(0), "A")
    exec.update(B(1,3), "A")
    exec.get[Int](B(0)) must beEqualTo(1)
    exec.get[Int](B(1)) must beEqualTo(2)
    exec.get[Int](B(2)) must beEqualTo(3)
    exec.get[Int](B(3)) must beEqualTo(4)
  }

}