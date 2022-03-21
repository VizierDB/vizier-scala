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

case class SpreadsheetConstructor ( 
  input: Option[Identifier],
  //subscriptions: RangeSet,
  //updates: mutable.Map[Long, UpdateRule],
  dag: mutable.Map[ColumnRef,RangeMap[UpdateRule]],
  //data: mutable.Map[ColumnRef, mutable.Map[Long, Future[Any]]],
  //triggers:  mutable.Map[ColumnRef, mutable.Map[Long, TriggerSet]], 
  frame: ReferenceFrame,
  schema: mutable.ArrayBuffer[OutputColumn]
) 
extends DataFrameConstructor
  with DefaultProvenance 
  {
    //def dependencies: Set[Identifier] = input.toSet

    def construct(context: Identifier => DataFrame): DataFrame = ???


    def testConstruct: Spreadsheet = ???


    def dependencies = input.toSet
}
object SpreadsheetConstructor 
  extends DataFrameConstructorCodec
{
  /**
  implicit val updatesWrites = new Writes[mutable.Map[Long, UpdateRule]] {
    def writes(m: mutable.Map[Long, UpdateRule]): JsValue = {
      val bigMap = mutable.Map.empty[String, JsValue]
      for((k, v) <- m) {
        bigMap(k.toString()) = Json.toJson(v)
      }
      val returnThis: JsObject = new JsObject(bigMap)
      return returnThis
    }
  }

  implicit val updatesReads = new Reads[mutable.Map[Long, UpdateRule]] {
    def reads(j: JsValue): JsResult[mutable.Map[Long, UpdateRule]] = {
      val data = j.as[Map[String, JsValue]]
      val returnThis = mutable.Map.empty[Long, UpdateRule]
      for((k, v) <- data) {
        returnThis(k.asInstanceOf[Long]) = data(k).asInstanceOf[UpdateRule]
      }
      JsSuccess(returnThis)
     }
  }
  **/
  
  /**
  implicit val dataWrites = new Writes[mutable.Map[ColumnRef, mutable.Map[Long, Future[Any]]]] {
    def writes(d: mutable.Map[ColumnRef, mutable.Map[Long, Future[Any]]]): JsValue = {
      val bigMap = mutable.Map.empty[String, JsValue]
      for((k, v) <- d) {
        bigMap(k.toString()) = Json.toJson(v)
      }
      val returnThis: JsObject = new JsObject(bigMap)
      return returnThis
    }
  }
**/
  /**
  implicit val dataReads = new Reads[mutable.Map[ColumnRef, mutable.Map[Long, Future[Any]]]] {
    def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, mutable.Map[Long, Future[Any]]]] = {
      val data = j.as[Map[String, JsValue]]
      val returnThis = mutable.Map.empty[ColumnRef, mutable.Map[Long, Future[Any]]]
      for((k, v) <- data) {
        returnThis(k.asInstanceOf[ColumnRef]) = data(k).asInstanceOf[ mutable.Map[Long, Future[Any]]]
      }
      JsSuccess(returnThis)
     }
  }
  **/
  /**
   implicit val triggersWrites = new Writes[mutable.Map[ColumnRef, mutable.Map[Long,TriggerSet]]] {
    def writes(t: mutable.Map[ColumnRef, mutable.Map[Long,TriggerSet]]): JsValue = {
     val bigMap = mutable.Map.empty[String, JsValue]
      for((k, v) <- t) {
        bigMap(k.toString()) = Json.toJson(v.toString()) //go back and fix later
      }
      val returnThis: JsObject = new JsObject(bigMap)
      return returnThis
   }
  }
  implicit val triggersReads = new Reads[mutable.Map[ColumnRef, mutable.Map[Long,TriggerSet]]] {
    def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, mutable.Map[Long,TriggerSet]]] = {
      val data = j.as[Map[String, JsValue]]
      val returnThis = mutable.Map.empty[ColumnRef, mutable.Map[Long, TriggerSet]]
      for((k, v) <- data) {
        returnThis(k.asInstanceOf[ColumnRef]) = data(k).asInstanceOf[mutable.Map[Long, TriggerSet]]
      }
      JsSuccess(returnThis)
    }
  }
  **/
  
  /**
  implicit val dagReads = new Reads[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
    def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = {
      val data = j.as[Map[String, JsValue]]
      val returnThis = mutable.Map.empty[ColumnRef, RangeMap[UpdateRule]]
      for((k, v) <- data) {
        returnThis(k.asInstanceOf[ColumnRef]) = data(k).asInstanceOf[RangeMap[UpdateRule]]
      }
      JsSuccess(returnThis)
     }
  }
  **/
  /**

  implicit val dagReads = new Reads[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
  def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = {
        JsSuccess(j.as[mutable.Map[ColumnRef, RangeMap[UpdateRule]]].map{case (k, v) =>
            k.asInstanceOf[JsValue].asInstanceOf[ColumnRef] -> v.asInstanceOf[RangeMap[UpdateRule]]
        })
    }
  }
  **/

  /**
  implicit val dagReads = new Reads[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
  override def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = 
  {
    JsSuccess(j.as[mutable.Map[ColumnRef, RangeMap[UpdateRule]]].map{case (k, v) =>
      k -> v
      //Json.parse(k).asInstanceOf[ColumnRef] -> v.asInstanceOf[RangeMap[UpdateRule]]
    })
  }
}

  
  implicit val dagWrites = new Writes[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
    def writes(dag: mutable.Map[ColumnRef, RangeMap[UpdateRule]]): JsValue = {
        Json.obj(dag.map{case (k, v) =>
            val ret: (String, Json.JsValueWrapper) = Json.toJson(k).toString -> Json.toJson(v)
            ret
        }.toSeq:_*)
    }
  }

  **/

  /**
  implicit val dagReads: Reads[Map[ColumnRef, RangeMap[UpdateRule]]] =
    Reads.mapReads[ColumnRef, RangeMap[UpdateRule]](s => JsResult.fromTry(Try(s.asInstanceOf[ColumnRef])))

  implicit val dagWrites: Writes[Map[ColumnRef, RangeMap[UpdateRule]]] =
    MapWrites.mapWrites[RangeMap[UpdateRule]].contramap(_.map { case (k, v) => k.toString -> v})


  implicit val dagWrites2 = new Writes[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
    def writes(dag: mutable.Map[ColumnRef, RangeMap[UpdateRule]]): JsValue = {
        val dag2 = dag.toMap
        Json.toJson(dag2)
  }
}
  implicit val dagReads2 = new Reads[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] {
  def reads(j: JsValue): JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = {
        val immutableDag = (Json.fromJson[Map[ColumnRef, RangeMap[UpdateRule]]](j).asOpt).get
        //val ret = mutable.Map(immutableDag.toSeq:_*)
        val ret = collection.mutable.Map() ++ immutableDag
        JsSuccess(ret)
    }
  }
**/

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
  //implicit val format: Format[SpreadsheetConstructor] = ???
  def apply(j: JsValue) = j.as[SpreadsheetConstructor]
}



