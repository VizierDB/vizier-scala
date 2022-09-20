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
import info.vizierdb.spark.vizual.Resolve
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
        var adjacencyList = mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]()
        for ((column, rule) <- dag) 
        {
            for((start, end) <- rule.data) 
            {
                val startOfChildNode = start
                val endOfChildNode = end._1
                val childUpdateRule = end._2
                val resultOfTriggeringRanges = childUpdateRule.triggeringRanges(startOfChildNode, endOfChildNode, childUpdateRule.frame)
                for((columnRef, rangeSet) <- resultOfTriggeringRanges)
                {
                    for(range <- rangeSet)
                    {
                        val parentNode = (columnRef, range._1, range._2)
                        val childNode = (column, startOfChildNode, endOfChildNode, childUpdateRule)
                        adjacencyList.getOrElseUpdate(parentNode, new mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]) += childNode
                    }
                }
            }

        }
        /**
        println(s"Adjacency list: \n")
        for((columnRef, listOfColumnRefs) <- adjacencyList){
            println(s"${columnRef} -> ")
            for(value <- listOfColumnRefs)
            {
                print(s"(ColumnRef: ${value._1}, start: ${value._2}, end: ${value._3}, UpdateRule: ${value._4}), \n")
            }
            println("")
        }
        **/
        def bfs(graph:  mutable.Map[(ColumnRef, Long, Long), mutable.ArrayBuffer[(ColumnRef, Long, Long, UpdateRule)]]): mutable.ArrayBuffer[(ColumnRef, Long, Long)] = {
            val inDegree = mutable.Map[(ColumnRef, Long, Long), Int]()
            val q = mutable.Queue[(ColumnRef, Long, Long)]()
            val explored = mutable.Map[(ColumnRef, Long, Long), Boolean]()
            val order = mutable.ArrayBuffer[(ColumnRef, Long, Long)]()
            for((node, neighbors) <- adjacencyList)
            {
                inDegree += ((node, 0))
                explored(node) = false
                for(neighbor <- neighbors){
                    inDegree += (((neighbor._1, neighbor._2, neighbor._3), 0))
                    explored((neighbor._1, neighbor._2, neighbor._3)) = false
                }
            }
            for((node, neighbors) <- adjacencyList) {
                for(neighbor <- neighbors) {
                    inDegree((neighbor._1, neighbor._2, neighbor._3)) += inDegree(node) + 1
                }
            }
            for((node, degree) <- inDegree)
            {
                if(degree == 0){
                    q.enqueue(node)
                }
            }
            while(!q.isEmpty) 
            {
                val next = q.dequeue
                explored(next) = true
                order += next
                if(adjacencyList isDefinedAt next)
                {
                    for(neighbor <- adjacencyList(next))
                    {
                        println(s"${neighbor}")
                        inDegree((neighbor._1, neighbor._2, neighbor._3)) -= 1
                        if(explored((neighbor._1, neighbor._2, neighbor._3)) == true)
                        {
                            println(s"CYCLE DETECTED: ${next} to ${(neighbor._1, neighbor._2, neighbor._3)}")
                        }
                        if(inDegree((neighbor._1, neighbor._2, neighbor._3)) == 0)
                        {
                            q.enqueue((neighbor._1, neighbor._2, neighbor._3))
                        } else {
                            //println(s"Not adding ${neighbor} to queue. inDegree: ${inDegree((neighbor._1, neighbor._2, neighbor._3))}")
                        }
                    }
                }
                else {
                    //println("Which does NOT have neighbors")
                }
            }
            if(explored.size != inDegree.size)
            {
                println("Cycle detected")
            }
            //println(explored)
            order
        }
        val order = bfs(adjacencyList)
        println(order)
        for (update <- order)
        {
            
            val start = update
            val destinations = adjacencyList.getOrElse(start, null)
            val targetColumn: StructField = input.schema.fields(start._1.id.toInt)
            if(destinations != null)
            {
                for (destination <- destinations)
                {
                    val cellRange = (destination._1, destination._2, destination._3)
                    val updateRule = (destination._4)
                    println(s"Update Rule: ${updateRule}")
                    val expression = updateRule.expression
                    println(s"Expression: ${expression}")
                    println(expression.getClass.getName)
                    
                    val expr = expression.transform {
                        case RValueExpression(SingleCell(_,_)) =>
                            {
                                println("single cell")
                                println(expression.references)
                                val rvalue: RValue = SingleCell(ColumnRef(1), 1)
                                RValueExpression(rvalue)
                                
                            }
                        case RValueExpression(OffsetCell(_,0)) =>
                            {
                                println("offset cell (_,0)")
                                println(expression.references)
                                //RValueExpression(rvalue)
                                val rvalue: RValue = SingleCell(ColumnRef(1), 1)
                                RValueExpression(rvalue)
                            }
                        case RValueExpression(OffsetCell(_,_)) =>
                            {
                                println("offset cell (_,_)")
                                println(expression.references)
                                val rvalue: RValue = SingleCell(ColumnRef(1), 1)
                                RValueExpression(rvalue)
                            }
                        case _ => 
                            {
                                println("Couldn't identify")
                                println(expression.getClass.getName)
                               // println(expression.output)
                                val rvalue: RValue = SingleCell(ColumnRef(1), 1)
                                RValueExpression(rvalue)
                            }
                    }
                }
            }
        }
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
