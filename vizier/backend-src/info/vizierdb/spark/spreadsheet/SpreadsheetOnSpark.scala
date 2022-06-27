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
        //Insert columns
        for(outputColumn <- schema) {
            outputColumn.source match {
                case DefaultValue(defalutValue, dataType) =>
                    {
                        val position = Some(outputColumn.position)
                        val column = outputColumn.output.name
                        
                        val columns = 
                            input.columns
                                .map { input(_) }
                                .toSeq
                        val (pre, post):(Seq[Column], Seq[Column]) = 
                            position.map { columns.splitAt(_) }
                                    .getOrElse { (columns, Seq()) }
                        input.select( ((pre :+ lit(null).cast(Some(dataType).getOrElse { StringType }).as(column)) ++ post):_* )
                    }
                case _ =>
            }
        }
        //Update reference frames
        for(transformation <- frame.transformations) {
            transformation match {
                case DeleteRows(position, count) =>
                    {
                        def deleteRow(rowid: Long): Unit = {
                            AnnotateWithRowIds.withRowId(input) { df => 
                                df.filter(col(AnnotateWithRowIds.ATTRIBUTE) =!= lit(rowid))
                            }
                        }
                        for(row <- position until position + count) {
                            deleteRow(position)
                        }
                    }
                case InsertRows(position, count, insertId) =>
                    {
                        val values = None
                        def insertRow: Unit = {
                            val newRowData = 
                                input.schema
                                    .zip(values.getOrElse { input.columns.toSeq.map { _ => JsNull } })
                                    .map { case (field, value) => 
                                    new Column(Literal(
                                        userFacingToInternalType(SparkPrimitive.decode(value, field.dataType), field.dataType),
                                        field.dataType
                                    )).as(field.name)
                                    }
                            if(position < 0){
                                input.union(
                                input.sqlContext
                                    .range(1)
                                    .select(newRowData:_*)
                                )
                            } else {
                                AnnotateWithSequenceNumber.withSequenceNumber(input){ df =>
                                val seq = df(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val oldRowData =
                                    input.columns.map { df(_) } :+ 
                                    when(seq >= position, seq + 1)
                                        .otherwise(seq)
                                        .as(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val newRowDataWithAttribute = 
                                    newRowData :+ lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val newRow = 
                                    input.sqlContext
                                        .range(1)
                                        .select(newRowDataWithAttribute:_*)

                                df.select(oldRowData:_*)
                                    .union(newRow)
                                    .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
                                }
                            }
                        }
                        for(insert <- 1 to count) {
                            insertRow
                        }
                    }
                case MoveRows(from, to, count) =>
                    {
                        def moveRow(row: Long, position: Long): Unit = {
                            AnnotateWithRowIds.withRowId(input) { rowDF =>
                                val targetDropped = 
                                rowDF.filter( rowDF(AnnotateWithRowIds.ATTRIBUTE) =!= lit(row) )
                                AnnotateWithSequenceNumber.withSequenceNumber(targetDropped){ df =>
                                val seq = df(AnnotateWithSequenceNumber.ATTRIBUTE)
                                val oldRowData =
                                    input.columns.map { df(_) } :+ 
                                    when(seq >= position, seq + 1)
                                        .otherwise(seq)
                                        .as(AnnotateWithSequenceNumber.ATTRIBUTE) :+
                                    df(AnnotateWithRowIds.ATTRIBUTE)
                                val replacedRowData = 
                                    input.columns
                                        .map { rowDF(_) } :+
                                            lit(position).as(AnnotateWithSequenceNumber.ATTRIBUTE) :+
                                    df(AnnotateWithRowIds.ATTRIBUTE)
                                val replacedRow = 
                                    rowDF.filter( rowDF(AnnotateWithRowIds.ATTRIBUTE) === lit(row) )
                                        .select(replacedRowData:_*)

                                df.select(oldRowData:_*)
                                    .union(replacedRow)
                                    .sort(col(AnnotateWithSequenceNumber.ATTRIBUTE).asc)
                                }
                            }
                        }
                        for(move <- 1 to count) {
                            moveRow(from, to)
                        }
                    }
            }   
        }
        //Apply dag ops
        
        //Remove columns
        input

    }
    def userFacingToInternalType(value: Any, dataType: DataType): Any =
    dataType match {
      // change this to UserDefinedType once we upgrade to spark 3.2
      case GeometryUDT => GeometryUDT.serialize(value.asInstanceOf[org.locationtech.jts.geom.Geometry])

      // most native types can be converted by evaluating the literal expression
      case _ => lit(value).expr.eval()
    }
}
