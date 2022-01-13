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
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.Literal

class UpdateDAGSpec
  extends Specification
  // with BeforeAll
{

  // def beforeAll = SharedTestResources.init

  class UpdateDAGTester extends UpdateDAG
  {
    var lastTrigger: Seq[(Long, Long, CellUpdate)] = Seq.empty

    def triggerReexecution(ranges: Iterable[(Long, Long, CellUpdate)]): Unit = 
      lastTrigger = ranges.toSeq
  }

  val A = ColumnRef(1)
  val B = ColumnRef(2)
  val C = ColumnRef(3)
  val D = ColumnRef(4)
  val E = ColumnRef(5)

  def cell(column: ColumnRef, row: Long) =
    new Column( RValueExpression(SingleCell(column, row), IntegerType) )

  def beUpdateLiteral(l: Any) =
    { x:CellUpdate => x.expression must beEqualTo(Literal(l)) }


  "Static Insertions" >> {
    val dag = new UpdateDAGTester

    dag.addColumn(A)
    dag.addColumn(B)

    // If we insert...
    dag.update(B(2), lit(1))
    
    // It should trigger a re-execution
    dag.lastTrigger must haveSize(1)
    dag.lastTrigger(0)._1 must beEqualTo(2)
    dag.lastTrigger(0)._2 must beEqualTo(2)
    dag.lastTrigger(0)._3.expression must beEqualTo(Literal(1))

    // And the new record should be retrievable
    dag.get(B, 2) must beSome( beUpdateLiteral(1) )

    // But shouldn't come back for any other cells
    dag.get(B, 3) must beNone
    dag.get(B, 1) must beNone
    dag.get(A, 2) must beNone
    dag.get(A, 1) must beNone

    //If we overwrite the insertions
    dag.update(B.all, lit(2))

    //It should trigger a flurry of updates
    dag.lastTrigger must haveSize(1)
    dag.lastTrigger(0)._1 must beEqualTo(0)
    dag.lastTrigger(0)._2 must beGreaterThan(20l)
    dag.lastTrigger(0)._3.expression must beEqualTo(Literal(2))

    //We should see this update
    dag.get(B, 1) must beSome( beUpdateLiteral(2) )
    dag.get(B, 2) must beSome( beUpdateLiteral(2) )

    //... but not in other columns
    dag.get(A, 2) must beNone

    // And if we overwrite the default update
    dag.update(B(2), lit(3))

    // We should see the default for other cells
    dag.get(B, 1) must beSome( beUpdateLiteral(2) )

    // And the new value for this cell
    dag.get(B, 2) must beSome( beUpdateLiteral(3) )

    dag.get(A, 1) must beNone
    dag.get(A, 2) must beNone


    // And now ranges
    dag.update(A(2, 4), lit(4))

    for(i <- 2 to 4)
      { dag.get(A, i) must beSome( beUpdateLiteral(4) ) }
    dag.get(A, 1) must beNone
    dag.get(A, 5) must beNone

    // println(dag.lvalueIndex(A))
    dag.update(A(0, 2), lit(5))
    // println(dag.lvalueIndex(A))

    for(i <- 0 to 2)
      { dag.get(A, i) must beSome( beUpdateLiteral(5) ) }
    for(i <- 3 to 4)
      { dag.get(A, i) must beSome( beUpdateLiteral(4) ) }

    dag.get(A, 5) must beNone

    dag.update(A(1, 5), lit(6))
    // println(dag.lvalueIndex(A))
    dag.get(A, 0) must beSome( beUpdateLiteral(5) )

    for(i <- 1 to 5)
      { dag.get(A, i) must beSome( beUpdateLiteral(6) )}
    dag.get(A, 6) must beNone

  }

  "Row Deletion" >> 
  {
    val dag = new UpdateDAGTester

    dag.addColumn(A)
    dag.addColumn(B)
    dag.addColumn(C)

    dag.update(A(2, 5), lit(1))
    dag.update(A(10, 15), lit(2))

    dag.update(B(3,7), lit(3))
    dag.update(B(8,20), lit(4))

    dag.update(C(7,20), lit(5))
    
    dag.deleteRows(8, 3) // Delete rows 8, 9, 10

    dag.get(A, 6) must beNone
    dag.get(A, 5) must beSome( beUpdateLiteral(1) )
    dag.get(A, 2) must beSome( beUpdateLiteral(1) )
    dag.get(A, 1) must beNone

    dag.get(A, 7) must beNone
    dag.get(A, 8) must beSome( beUpdateLiteral(2) )
    dag.get(A, 9) must beSome( beUpdateLiteral(2) )
    dag.get(A, 12) must beSome( beUpdateLiteral(2) )
    dag.get(A, 13) must beNone

    dag.get(B, 2) must beNone
    dag.get(B, 3) must beSome( beUpdateLiteral(3) )
    dag.get(B, 7) must beSome( beUpdateLiteral(3) )
    dag.get(B, 8) must beSome( beUpdateLiteral(4) )
    dag.get(B, 17) must beSome( beUpdateLiteral(4) )
    dag.get(B, 18) must beNone

    dag.get(C, 6) must beNone
    dag.get(C, 7) must beSome( beUpdateLiteral(5) )
    dag.get(C, 8) must beSome( beUpdateLiteral(5) )
    dag.get(C, 9) must beSome( beUpdateLiteral(5) )
    dag.get(C, 10) must beSome( beUpdateLiteral(5) )
    dag.get(C, 11) must beSome( beUpdateLiteral(5) )
    dag.get(C, 17) must beSome( beUpdateLiteral(5) )
    dag.get(C, 18) must beNone

    dag.update(A(4, 10), lit(6))

    dag.get(A, 3) must beSome( beUpdateLiteral(1) )
    dag.get(A, 4) must beSome( beUpdateLiteral(6) )
    dag.get(A, 7) must beSome( beUpdateLiteral(6) )
    dag.get(A, 8) must beSome( beUpdateLiteral(6) )
    dag.get(A, 9) must beSome( beUpdateLiteral(6) )
    dag.get(A, 10) must beSome( beUpdateLiteral(6) )
    dag.get(A, 11) must beSome( beUpdateLiteral(2) )
  }

  "Row Insertions" >>
  {
    val dag = new UpdateDAGTester

    dag.addColumn(A)

    dag.update(A(1, 3), lit(1))
    dag.update(A(4, 7), lit(2))
    dag.update(A(8, 10), lit(3))

    // println(dag.lvalueIndex(A))
    dag.insertRows(5, 3)
    // println(dag.lvalueIndex(A))

    dag.get(A, 0) must beNone
    dag.get(A, 1) must beSome( beUpdateLiteral(1) )
    dag.get(A, 3) must beSome( beUpdateLiteral(1) )
    dag.get(A, 4) must beSome( beUpdateLiteral(2) )
    dag.get(A, 9) must beSome( beUpdateLiteral(2) )
    dag.get(A, 10) must beSome( beUpdateLiteral(2) )
    dag.get(A, 11) must beSome( beUpdateLiteral(3) )
    dag.get(A, 13) must beSome( beUpdateLiteral(3) )
    dag.get(A, 14) must beNone


    dag.get(A, 5) must beSome( beUpdateLiteral(2) )
    dag.get(A, 6) must beSome( beUpdateLiteral(2) )
  }

  "Row Moves" >> 
  {
    val dag = new UpdateDAGTester

    dag.addColumn(A)

    dag.update(A(1, 3), lit(1))
    dag.update(A(4, 7), lit(2))
    dag.update(A(8, 10), lit(3))

    // println("BEFORE MOVE")
    // println(dag.lvalueIndex(A))
    dag.moveRows(5, 9, 3)
    // println(dag.lvalueIndex(A))
    // println("AFTER MOVE")

    dag.get(A, 0) must beNone
    dag.get(A, 1) must beSome( beUpdateLiteral(1) )
    dag.get(A, 3) must beSome( beUpdateLiteral(1) )
    dag.get(A, 4) must beSome( beUpdateLiteral(2) )
    dag.get(A, 5) must beSome( beUpdateLiteral(3) )
    dag.get(A, 6) must beSome( beUpdateLiteral(2) )
    dag.get(A, 7) must beSome( beUpdateLiteral(2) )
    dag.get(A, 8) must beSome( beUpdateLiteral(2) )
    dag.get(A, 9) must beSome( beUpdateLiteral(3) )
    dag.get(A, 10) must beSome( beUpdateLiteral(3) )
    dag.get(A, 11) must beNone
  }

  "Static Dependencies" >> 
  {
    val dag = new UpdateDAGTester

    dag.addColumn(A)

    dag.update(A(2), A(1).ref + 1)
    dag.lastTrigger = Seq.empty

    dag.update(A(1), lit(1))
    
    { 
      val deps = dag.getFlatDependents(A, RangeSet(1))
      deps must haveSize(1)
      deps(0)._1 must beEqualTo(2)
      deps(0)._2 must beEqualTo(2)
    }
    { 
      val deps = dag.getFlatDependents(A, RangeSet(2))
      deps must beEmpty
    }

    // println(dag.rvalueIndex(A))

    dag.addColumn(B)
    dag.update(B(1, 2), (A offsetBy 0).ref)

    // println(dag.rvalueIndex(A))

    //coalesce dependents
    dag.getDependents(A, RangeSet(1,2))
       .filter { _._2.target.column == B }
       .map { _._1 }
       .foldLeft(RangeSet()) { _ ++ _ }
       .toSeq must contain(exactly((1l, 2l)))

    dag.addColumn(C)
    dag.update(C(1, 2), (A offsetBy 0).ref + (B offsetBy 1).ref)

    // If A(1, 2) is invalidated, it should trigger an update to C(1,2)
    dag.getDependents(A, RangeSet(1,2))
       .filter { _._2.target.column == C }
       .map { _._1 }
       .foldLeft(RangeSet()) { _ ++ _ }
       .toSeq must contain(exactly((1l, 2l)))

    // If B(1, 2) is invalidated, it should trigger an update to C(1)
    // (since C(2) depends on B(3))
    dag.getDependents(B, RangeSet(1,2))
       .filter { _._2.target.column == C }
       .map { _._1 }
       .foldLeft(RangeSet()) { _ ++ _ }
       .toSeq must contain(exactly((1l, 1l)))

    // If B(1,3) is invalidated, it should trigger an update to C(1,2)
    dag.getDependents(B, RangeSet(1,3))
       .filter { _._2.target.column == C }
       .map { _._1 }
       .foldLeft(RangeSet()) { _ ++ _ }
       .toSeq must contain(exactly((1l, 2l)))
  }

}

