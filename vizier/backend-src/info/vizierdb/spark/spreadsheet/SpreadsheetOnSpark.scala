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
    def apply(input: DataFrame, dag: mutable.Map[ColumnRef,RangeMap[UpdateRule]], frame: ReferenceFrame, schema: mutable.ArrayBuffer[OutputColumn]){
        
    }
}
