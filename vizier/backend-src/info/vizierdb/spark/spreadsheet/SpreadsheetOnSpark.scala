package info.vizierdb.spark.spreadsheet

import info.vizierdb.spreadsheet._
import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, Column }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ StructField, StringType, DataType }
import org.apache.spark.sql.catalyst.expressions.{ Expression, Cast, Literal }
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import info.vizierdb.spark.SparkPrimitive
import org.mimirdb.caveats.implicits._
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import scala.collection.mutable
import scala.concurrent.Future
import org.rogach.scallop.throwError
import scala.util.{Try, Success, Failure}

object SpreadsheetOnSpark extends LazyLogging{
    def apply(input: DataFrame, dag: mutable.Map[ColumnRef,RangeMap[UpdateRule]], frame: ReferenceFrame, schema: mutable.ArrayBuffer[OutputColumn]): DataFrame =
    {
        println("Input Dataframe:")
        input.show()
        var output = input

        //Insert columns
        for(outputColumn <- schema) {
            outputColumn.source match {
                case DefaultValue(defalutValue, dataType) =>
                    {
                        //println("outputColumn is DefaultValue")
                        val position = Some(outputColumn.position)
                        val column = outputColumn.output.name
                        val columns = 
                            output.columns
                                .map { output(_) }
                                .toSeq
                        val (pre, post):(Seq[Column], Seq[Column]) = 
                            position.map { columns.splitAt(_) }
                                    .getOrElse { (columns, Seq()) }
                        output = output.select( ((pre :+ lit(null).cast(Some(dataType).getOrElse { StringType }).as(column)) ++ post):_* )
                        //output.show()
                    }
                case _ => //println("other")
            }
        }

        //Update reference frames
        for(transformation <- frame.transformations) {
            transformation match {
                case DeleteRows(position, count) =>
                    {
                        def deleteRow(rowid: Long): DataFrame = {
                            output = AnnotateWithSequenceNumber.withSequenceNumber(output) { df => 
                                df.filter(col(AnnotateWithSequenceNumber.ATTRIBUTE) =!= lit(rowid))
                            }
                            output
                        }
                        for(row <- position until position + count) {
                            output = deleteRow(position - 1)
                        }
                    }
                case InsertRows(position, count, insertId) =>
                    {
                        val values = None
                        def insertRow: DataFrame = {
                            val newRowData = 
                                output.schema
                                    .zip(values.getOrElse { output.columns.toSeq.map { _ => JsNull } })
                                    .map { case (field, value) => 
                                    new Column(Literal(
                                        userFacingToInternalType(SparkPrimitive.decode(value, field.dataType), field.dataType),
                                        field.dataType
                                    )).as(field.name)
                                    }
                            if(position < 0){
                                output.union(
                                output.sqlContext
                                    .range(1)
                                    .select(newRowData:_*)
                                )
                            } else {
                                output = AnnotateWithSequenceNumber.withSequenceNumber(output){ df =>
                                val seq = df(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val oldRowData =
                                    output.columns.map { df(_) } :+ 
                                    when(seq >= position, seq + 1)
                                        .otherwise(seq)
                                        .as(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val newRowDataWithAttribute = 
                                    newRowData :+ lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val newRow = 
                                    output.sqlContext
                                        .range(1)
                                        .select(newRowDataWithAttribute:_*)

                                    df.select(oldRowData:_*)
                                    .union(newRow)
                                    .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
                                }
                            }
                            output
                        }
                        for(insert <- 1 to count) {
                            output = insertRow
                        }
                    }
                case MoveRows(from, to, count) =>
                    {
                        def moveRow(row: Long, position: Long): DataFrame = {
                            output = AnnotateWithSequenceNumber.withSequenceNumber(output) { rowDF =>
                                val targetDropped = 
                                    rowDF.filter( rowDF(AnnotateWithSequenceNumber.ATTRIBUTE) =!= row )
                                val seq = targetDropped(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val oldRowData =
                                    output.columns.map { targetDropped(_) } :+ 
                                        when(seq >= position, seq + 1)
                                            .otherwise(seq)
                                            .as(AnnotateWithSequenceNumber.ATTRIBUTE) 
                                val replacedRowData = 
                                    output.columns
                                        .map { rowDF(_) } :+
                                            lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE) 
                                val replacedRow = 
                                    rowDF.filter( rowDF(AnnotateWithSequenceNumber.ATTRIBUTE) === row )
                                        .select(replacedRowData:_*)

                                targetDropped.select(oldRowData:_*)
                                    .union(replacedRow)
                                    .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
                            }
                            output
                        }
                        for(move <- 1 to count) {
                            output = moveRow(from - 1, to - 1)
                        }
                    }
            }   
        }
        //Apply dag ops
        /**
        println("")
        var adjacencyList = mutable.Map[(ColumnRef, RangeSet), mutable.Map[ColumnRef, RangeSet]]()
        for ((column, rule) <- dag) 
        {
            println(s"\nCOLUMN: ${column}\nRULE: ${rule}")
            for((start, end) <- rule.data) 
            {
                println("\nOddity:")
                end._2.expression.foreach { node =>
                    println(s"xxx: ${node.getClass.getName}")
                }
                println("\n")
                val outNodes = end._2.triggeringRanges(start, end._1)
                println(s"start: ${start}")
                println(s"end._1: ${end._1}")
                println(s"end._2: ${end._2}")
                println(s"result of rvalues: ${end._2.rvalues}")
                println(s"Result of triggeringRanges: \n${outNodes}")
                for((columnRef, rangeSet) <- outNodes) 
                {
                    adjacencyList(columnRef, rangeSet)(column) = RangeSet(start, end._1)
                }
            }
        }
        println(s"Adjacency list: \n${adjacencyList}")
        //
       // var adjList = //Rule -> dependant cells
        **/
        var adjacencyList = mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]()
        for ((column, rule) <- dag) 
        {
            println(s"DAG column: ${column}")
            println(s"DAG RangeMap[UpdateRule]: ${rule}")
            for((start, end) <- rule.data) 
            {
                val startOfChildNode = start
                val endOfChildNode = end._1
                val childUpdateRule = end._2

                println(s"Start of child node: ${startOfChildNode}")
                println(s"End of child node: ${endOfChildNode}")
                println(s"Child node UpdateRule: ${childUpdateRule}\n")

                val resultOfTriggeringRanges = childUpdateRule.triggeringRanges(startOfChildNode, endOfChildNode, childUpdateRule.frame)
                println(s"Result of UpdateRule.triggeringRanges (Map[ColumnRef, RangeSet]):\n${resultOfTriggeringRanges}")
                for((columnRef, rangeSet) <- resultOfTriggeringRanges)
                {
                    for(range <- rangeSet)
                    {
                        val parentNode = (columnRef, range._1, range._2)
                        val childNode = (column, startOfChildNode, endOfChildNode, childUpdateRule)
                        println(s"Fully formed parent node: ${parentNode}")
                        println(s"Fully formed child node: ${childNode}")
                        adjacencyList.getOrElseUpdate(parentNode, new mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]) += childNode
                    }
                }
            }

        }
        println(s"Adjacency list: \n")
        for((columnRef, listOfColumnRefs) <- adjacencyList){
            println(s"${columnRef} -> ")
            for(value <- listOfColumnRefs)
            {
                print(s"(ColumnRef: ${value._1}, start: ${value._2}, end: ${value._3}, UpdateRule: ${value._4}), \n")
            }
            println("")
        }
        /**
        val explored = mutable.Map[(ColumnRef, Long, Long), Boolean]()
        for((node, neighbors) <- adjacencyList)
            {
                explored(node) = false
                println(s"Outside loop key: ${node}")
                for(neighbor <- neighbors)
                {
                    explored((neighbor._1, neighbor._2, neighbor._3)) = false
                }
            }
        def bfsOne(start: (ColumnRef, Long, Long)): Unit = {
            
            val q = mutable.Queue[(ColumnRef, Long, Long)]()
            
            q.enqueue(start)
            explored(start) = true
            while(!q.isEmpty)
            {
                val next = q.dequeue
                if(adjacencyList isDefinedAt next){
                    for(neighbor <- adjacencyList(next)){
                        if(explored((neighbor._1, neighbor._2, neighbor._3)) == true)
                        {
                            println(s"CYCLE DETECTED: ${next} to ${(neighbor._1, neighbor._2, neighbor._3)}")
                        }
                        else {
                            println(s"${(neighbor._1, neighbor._2, neighbor._3)} DISCOVERED FROM PARENT ${next}")
                            explored((neighbor._1, neighbor._2, neighbor._3)) = true
                        }
                        q.enqueue((neighbor._1, neighbor._2, neighbor._3))
                    }
                } else {
                    println(s"${next} has no dependencies")
                }


            }
        }
        def bfs(graph:  mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]): Unit = {
            for((vertex, e) <- explored)
            {
                println(s"${vertex}: ${e}")
                if(e == false){
                    bfsOne(vertex)
                }
            }
        }
        bfs(adjacencyList)

        **/


        /**
        def topologicalOrdering(graph:  mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]): mutable.ArrayBuffer[(ColumnRef, Long, Long)] =
        {
            var topOrdering = mutable.ArrayBuffer[(ColumnRef, Long, Long)]()
            var remaining = mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]()
            var baseCase = true
            for((node, neighbors) <- graph)
            {
                println(s"Outside loop key: ${node}")
                for(neighbor <- neighbors)
                {
                    val neighborKey = (neighbor._1, neighbor._2, neighbor._3)
                    println(s"neighborKey ${neighborKey}")
                    if(graph.getOrElse(neighborKey, null) == null)
                    {
                        println(s"${neighborKey} was not a member of input graph")
                        topOrdering += neighborKey
                        
                    } 
                    else
                    {
                        println(s"${neighborKey} WAS a member of input graph")
                        remaining += ((neighborKey, graph(neighborKey)))
                        baseCase = false
                    }
                }
            }
            if(baseCase) {
                println(s"Base case with topOrdering: ${topOrdering}")
                return topOrdering
            } 
            else 
            {
                println("Not the base case")
                println(s"Concatenating topologicalOrdering: ${topOrdering}")
                println(s"With topologicalOrdering(remaining). Remaining: ${remaining}")
                return topOrdering ++ topologicalOrdering(remaining)
            }
            
        }
        val topOrdering = topologicalOrdering(adjacencyList)
        println(s"Topological ordering: ${topOrdering}")
        **/


        //Remove columns

        
        output

    }
    def userFacingToInternalType(value: Any, dataType: DataType): Any =
    dataType match {
      // change this to UserDefinedType once we upgrade to spark 3.2
      case GeometryUDT => GeometryUDT.serialize(value.asInstanceOf[org.locationtech.jts.geom.Geometry])

      // most native types can be converted by evaluating the literal expression
      case _ => lit(value).expr.eval()
    }
}
