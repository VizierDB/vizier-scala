package info.vizierdb.spark.spreadsheet

import info.vizierdb.spreadsheet._
import play.api.libs.json._
import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import org.apache.spark.sql.types.DataType
import info.vizierdb.spark.SparkSchema.dataTypeFormat
import info.vizierdb.types._
import info.vizierdb.spark.DataFrameConstructorCodec
import info.vizierdb.spark.{ DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance }
import org.apache.spark.sql.{ DataFrame, SparkSession }
import play.api.libs.json.JsNull
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import scala.collection.mutable
import scala.concurrent.Future
import org.rogach.scallop.throwError
import scala.util.{Try, Success, Failure}
import info.vizierdb.Vizier

case class SpreadsheetConstructor ( 
  input: Option[Identifier],
  dag: mutable.Map[ColumnRef,RangeMap[UpdateRule]],
  frame: ReferenceFrame,
  schema: mutable.ArrayBuffer[OutputColumn]
) 
extends DataFrameConstructor
  with DefaultProvenance 
  {
    def construct(context: Identifier => DataFrame): DataFrame = 
    {
      println("spreadsheetconstructor")
      SpreadsheetOnSpark(
      input.map{ context(_) }
           .getOrElse { Vizier.sparkSession.emptyDataFrame },
           dag, frame, schema
      )
    }

    //def testConstruct: Spreadsheet = ???
    def dependencies = input.toSet
}
object SpreadsheetConstructor 
  extends DataFrameConstructorCodec
{
  implicit val dagWrites = new Writes[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
     def writes(dag: mutable.Map[ColumnRef, RangeMap[UpdateRule]]): JsValue = {
       val d = dag.toSeq
       Json.toJson(d)
     }
  }

  implicit val dagReads = new Reads[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
    def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = {
      val dagList = (j.as[Seq[(ColumnRef, RangeMap[UpdateRule])]]).toMap
      val dag = mutable.Map(dagList.toSeq: _*)
      JsSuccess(dag)
    }
  }
  implicit val dagFormat: Format[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = Format(dagReads, dagWrites)
  
  implicit val format: Format[SpreadsheetConstructor] = Json.format
  def apply(j: JsValue) = j.as[SpreadsheetConstructor]
}



