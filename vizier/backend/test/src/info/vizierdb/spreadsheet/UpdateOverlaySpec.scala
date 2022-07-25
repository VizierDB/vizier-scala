package info.vizierdb.spreadsheet

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

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.Literal
import scala.concurrent.duration._
import org.apache.spark.sql.catalyst.expressions.Add

class UpdateOverlaySpec
  extends Specification
  // with BeforeAll
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val A = ColumnRef(1, "A")
  val B = ColumnRef(2, "B")
  val C = ColumnRef(3, "C")

  def cell(column: ColumnRef, row: Long) =
    new Column( RValueExpression(SingleCell(column, row)) )

  def init(): UpdateOverlay = 
  {
    val overlay = new UpdateOverlay( (_, _) => 0 )
    overlay.subscribe(RangeSet(0, 19))
    return overlay
  }

  "Rvalues" >> {
    val rvalue1: RValue = SingleCell(A, 0)
    val rvalue2: RValue = OffsetCell(A, 0)
    val rule = UpdateRule(
                  Add(
                    RValueExpression(rvalue1),
                    RValueExpression(rvalue2)
                  ), 
                  ReferenceFrame(),
                  0
                )
    rule.rvalues must contain(exactly(rvalue1, rvalue2))
  }


  "Static Insertions" >> {
    val overlay = init

    overlay.addColumn(A)
    overlay.addColumn(B)

    // If we insert...
    overlay.update(B(2), lit(1))

    // And the new record should be retrievable
    overlay.await(B(2), duration = 1.second) must beEqualTo(1)

    // But shouldn't come back for any other cells
    overlay.await(B(3), duration = 1.second) must beEqualTo(0)
    overlay.await(B(1), duration = 1.second) must beEqualTo(0)
    overlay.await(A(2), duration = 1.second) must beEqualTo(0)
    overlay.await(A(1), duration = 1.second) must beEqualTo(0)

    //If we overwrite the insertions
    overlay.update(B(0,19), lit(2))

    //We should see this update
    overlay.await(B(1), duration = 1.second) must beEqualTo(2)
    overlay.await(B(2), duration = 1.second) must beEqualTo(2)

    //... but not in other columns
    overlay.await(A(2), duration = 1.second) must beEqualTo(0)

    // And if we overwrite the default update
    overlay.update(B(2), lit(3))

    // We should see the default for other cells
    overlay.await(B(1), duration = 1.second) must beEqualTo(2)

    // And the new value for this cell
    overlay.await(B(2), duration = 1.second) must beEqualTo(3)

    // And now ranges
    overlay.update(A(2, 4), lit(4))

    for(i <- 2 to 4)
      { overlay.await(A(i), duration = 1.second) must beEqualTo(4) }
    overlay.await(A(1), duration = 1.second) must beEqualTo(0)
    overlay.await(A(5), duration = 1.second) must beEqualTo(0)

    // println(dag.lvalueIndex(A))
    overlay.update(A(0, 2), lit(5))
    // println(dag.lvalueIndex(A))

    for(i <- 0 to 2)
      { overlay.await(A(i), duration = 1.second) must beEqualTo(5) }
    for(i <- 3 to 4)
      { overlay.await(A(i), duration = 1.second) must beEqualTo(4) }

    overlay.await(A(5), duration = 1.second) must beEqualTo(0)

    overlay.update(A(1, 5), lit(6))
    // println(dag.lvalueIndex(A))
    overlay.await(A(0), duration = 1.second) must beEqualTo(5)

    for(i <- 1 to 5)
      { overlay.await(A(i), duration = 1.second) must beEqualTo(6) }
    overlay.await(A(6), duration = 1.second) must beEqualTo(0)

  }

  "Row Deletion" >> 
  {
    val overlay = init()
    def get(cell: SingleCell) = overlay.await(cell, duration = 1.second)

    overlay.addColumn(A)
    overlay.addColumn(B)
    overlay.addColumn(C)

    overlay.update(A(2, 5), lit(1))
    overlay.update(A(10, 15), lit(2))

    overlay.update(B(3,7), lit(3))
    overlay.update(B(8,20), lit(4))

    overlay.update(C(7,20), lit(5))
    
    overlay.deleteRows(8, 3) // Delete rows 8, 9, 10

    get(A(6)) must beEqualTo(0)
    get(A(5)) must beEqualTo(1)
    get(A(2)) must beEqualTo(1)
    get(A(1)) must beEqualTo(0)

    get(A(7)) must beEqualTo(0)
    get(A(8)) must beEqualTo(2)
    get(A(9)) must beEqualTo(2)
    get(A(12)) must beEqualTo(2)
    get(A(13)) must beEqualTo(0)

    get(B(2)) must beEqualTo(0)
    get(B(3)) must beEqualTo(3)
    get(B(7)) must beEqualTo(3)
    get(B(8)) must beEqualTo(4)
    get(B(17)) must beEqualTo(4)
    get(B(18)) must beEqualTo(0)

    get(C(6)) must beEqualTo(0)
    get(C(7)) must beEqualTo(5)
    get(C(8)) must beEqualTo(5)
    get(C(9)) must beEqualTo(5)
    get(C(10)) must beEqualTo(5)
    get(C(11)) must beEqualTo(5)
    get(C(17)) must beEqualTo(5)
    get(C(18)) must beEqualTo(0)

    overlay.update(A(4, 10), lit(6))

    get(A(3)) must beEqualTo(1)
    get(A(4)) must beEqualTo(6)
    get(A(7)) must beEqualTo(6)
    get(A(8)) must beEqualTo(6)
    get(A(9)) must beEqualTo(6)
    get(A(10)) must beEqualTo(6)
    get(A(11)) must beEqualTo(2)
  }

  "Row Insertions" >>
  {
    val overlay = init()
    def get(cell: SingleCell) = overlay.await(cell, duration = 1.second)

    overlay.addColumn(A)

    overlay.update(A(1, 3), lit(1))
    overlay.update(A(4, 7), lit(2))
    overlay.update(A(8, 10), lit(3))

    // println(overlay.lvalueIndex(A))
    overlay.insertRows(5, 3)
    // println(overlay.lvalueIndex(A))

    get(A(0)) must beEqualTo(0)
    get(A(1)) must beEqualTo(1)
    get(A(3)) must beEqualTo(1)
    get(A(4)) must beEqualTo(2)
    get(A(9)) must beEqualTo(2)
    get(A(10)) must beEqualTo(2)
    get(A(11)) must beEqualTo(3)
    get(A(13)) must beEqualTo(3)
    get(A(14)) must beEqualTo(0)

    get(A(5)) must beEqualTo(2)
    get(A(6)) must beEqualTo(2)
  }

  "Row Moves" >> 
  {
    val overlay = init()
    def get(cell: SingleCell) = overlay.await(cell, duration = 1.second)

    overlay.addColumn(A)

    overlay.update(A(1, 3), lit(1))
    overlay.update(A(4, 7), lit(2))
    overlay.update(A(8, 10), lit(3))

    // 1   2   3   4   5   6    7    8   9  10
    // Before:
    // 1   1   1   2   2   2    2    3   3   3
    // After
    // 1   1   1   2   3   2    2    2   3   3
    // Indices: 
    // 1   2   3   4   8   5    6    7   9  10

    // println("BEFORE MOVE")
    // println(dag.lvalueIndex(A))
    overlay.moveRows(from = 5, to = 9, count = 3)
    // println(dag.lvalueIndex(A))
    // println("AFTER MOVE")

    get(A(0)) must beEqualTo(0)
    get(A(1)) must beEqualTo(1)
    get(A(3)) must beEqualTo(1)
    get(A(4)) must beEqualTo(2)
    get(A(5)) must beEqualTo(3)
    get(A(6)) must beEqualTo(2)
    get(A(7)) must beEqualTo(2)
    get(A(8)) must beEqualTo(2)
    get(A(9)) must beEqualTo(3)
    get(A(10)) must beEqualTo(3)
    get(A(11)) must beEqualTo(0)
  }

  "Static Dependencies" >> 
  {
    val overlay = init()
    def get(cell: SingleCell) = overlay.await(cell, duration = 2.seconds)

    overlay.addColumn(A)

    overlay.update(A(2), A(1).ref + 1)
    get(A(2)) must beEqualTo(1)

    overlay.update(A(1), lit(1))
    get(A(2)) must beEqualTo(2)
    
    overlay.addColumn(B)
    overlay.update(B(1, 2), (A offsetBy 0).ref)
    get(B(1)) must beEqualTo(1)
    get(B(2)) must beEqualTo(2)

    overlay.addColumn(C)
    overlay.update(C(1, 2), (A offsetBy 0).ref + (B offsetBy 1).ref)
    // C(1) = A(1) [1] + B(2) [2] 
    get(C(1)) must beEqualTo(3)
    // C(1) = A(2) [2] + B(3) [0] 
    get(C(2)) must beEqualTo(2)

    overlay.update(A(1), lit(3))
    get(A(2)) must beEqualTo(4)

    get(B(1)) must beEqualTo(3)
    get(B(2)) must beEqualTo(4)

    get(C(1)) must beEqualTo(7)
    get(C(2)) must beEqualTo(4)

    overlay.update(B(3), lit(2))
    get(C(1)) must beEqualTo(7)
    get(C(2)) must beEqualTo(6)

  }

}