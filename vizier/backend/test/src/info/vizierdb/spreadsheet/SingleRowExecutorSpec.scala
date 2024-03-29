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
  val D = ColumnRef(4, label = "D")
  val E = ColumnRef(5, label = "E")

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

  "Multi-hop References" >> 
  {
    val exec = init(
      Map( 
        A -> Seq(1, 2, 3),
        B -> Seq(3, 4, 5),
        C -> Seq(6, 7, 8),
      )
    )

    exec.get[Int](B(0)) must beEqualTo(3)

    exec.update(B(0), "A")
    exec.update(C(0), "B")

    exec.update(A(1), "B")
    exec.update(C(1), "A")

    exec.update(A(2), "B")
    exec.update(B(2), "C")

    for(
      c <- Seq(A, B, C);
      (r, v) <- Seq( 0 -> 1, 1 -> 4, 2 -> 8 )
    ) yield
    {
      exec.get[Int](c(r)) must beEqualTo(v)
    }
  }

  "Triggered Recomputation" >> 
  {
    val exec = init(
      Map( 
        A -> Seq(1, 2, 3),
        B -> Seq(3, 4, 5),
        C -> Seq(6, 7, 8),
      )
    )

    exec.update(C(0), "B")
    exec.get[Int](C(0)) must beEqualTo(3)
    exec.update(B(0), "10")
    exec.get[Int](C(0)) must beEqualTo(10)
  }

  "Error on deletion" >>
  {
    val exec = init(
      Map( 
        A -> Seq(1, 2, 3),
        B -> Seq(3, 4, 5),
        C -> Seq(6, 7, 8),
      )
    )

    exec.update(C(0), "B")
    exec.get[Int](C(0)) must beEqualTo(3)
    exec.deleteColumn(B)
    exec.columns.keys must not contain(B)
    
    val err = exec.getFuture(C, 0)
    Await.ready(err, Duration(10, TimeUnit.SECONDS))
    err.value.get.isFailure must beTrue
    
  }

  "Formulas" >>
  {
    val exec = init(
      Map( 
        A -> Seq(1, 2, 3),
        B -> Seq(3, 4, 5),
        C -> Seq(6, 7, 8),
      )
    )

    exec.addColumn(D)
    exec.addColumn(E)
    exec.update(D(0), "A + B")     // 1 + 3 = 4
    exec.update(E(0), "B * C + D") // 3 * 6 + 4 = 22

    exec.get[Int](D(0)) must beEqualTo(4)
    exec.get[Int](E(0)) must beEqualTo(22)

    exec.deleteColumn(D)

    val err = exec.getFuture(E, 0)
    Await.ready(err, Duration(10, TimeUnit.SECONDS))
    err.value.get.isFailure must beTrue
  }

  "Deleted rows" >>
  {
    val exec = init(
      Map( 
        A -> Seq(1,  2,  3,  4,  5),
        B -> Seq(11, 12, 13, 14, 15),
        C -> Seq(21, 22, 23, 24, 25),
      )
    )

    exec.get[Int](A(4)) must beEqualTo(5)
    exec.get[Int](B(4)) must beEqualTo(15)
    exec.update(B(4), "A")

    exec.deleteRows(RangeSet(Seq(1l->3l)))

    exec.get[Int](A(0)) must beEqualTo( 1)
    exec.get[Int](B(0)) must beEqualTo(11)
    exec.get[Int](C(0)) must beEqualTo(21)

    exec.get[Int](A(1)) must beEqualTo( 5)
    exec.get[Int](B(1)) must beEqualTo( 5)
    exec.get[Int](C(1)) must beEqualTo(25)
  }

  "Inserted rows" >>
  {
    val exec = init(
      Map( 
        A -> Seq(1,  2,  3),
        B -> Seq(11, 12, 13),
        C -> Seq(21, 22, 23),
      )
    )

    exec.get[Int](A(2)) must beEqualTo(3)
    exec.get[Int](B(2)) must beEqualTo(13)
    exec.update(B(2), "A")

    exec.insertRows(1, 2)

    exec.get[Int](A(0)) must beEqualTo( 1)
    exec.get[Int](B(0)) must beEqualTo(11)
    exec.get[Int](C(0)) must beEqualTo(21)

    exec.get[Any](A(1)) must beNull
    exec.get[Any](A(2)) must beNull

    exec.get[Int](A(3)) must beEqualTo( 2)
    exec.get[Int](B(3)) must beEqualTo(12)
    exec.get[Int](C(3)) must beEqualTo(22)
  }

  "Moved rows" >>
  {
    val exec = init(
      Map( 
        A -> Seq(1,  2,  3),
        B -> Seq(11, 12, 13),
        C -> Seq(21, 22, 23),
      )
    )

    exec.get[Int](A(1)) must beEqualTo(2)
    exec.get[Int](B(1)) must beEqualTo(12)
    exec.update(B(1), "A")

    exec.moveRows(1, 2, 1)

    exec.get[Int](A(0)) must beEqualTo( 1)
    exec.get[Int](B(0)) must beEqualTo(11)
    exec.get[Int](C(0)) must beEqualTo(21)

    exec.get[Int](A(1)) must beEqualTo( 3)
    exec.get[Int](B(1)) must beEqualTo(13)
    exec.get[Int](C(1)) must beEqualTo(23)

    exec.get[Int](A(2)) must beEqualTo( 2)
    exec.get[Int](B(2)) must beEqualTo( 2)
    exec.get[Int](C(2)) must beEqualTo(22)

  }
}