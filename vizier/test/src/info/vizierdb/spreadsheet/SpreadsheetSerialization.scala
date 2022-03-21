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

import scalikejdbc.DB
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types._
import info.vizierdb.spark.vizual._
import info.vizierdb.commands._
import scala.concurrent.duration._


import play.api.libs.functional.syntax._

import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor.dagFormat
import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor.dagWrites
import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor
import scala.collection.mutable
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.IntegerType

class SpreadsheetSerialization
    extends Specification
    with BeforeAll
{

    
    def beforeAll = SharedTestResources.init

    
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    //lazy val project = MutableProject("Spreadsheet Serialization Test")
    /**
    lazy val project = MutableProject("Spreadsheet Serialization Test")
    
    val A = ColumnRef(1, "A")
    val B = ColumnRef(2, "B")
    val C = ColumnRef(3, "C")

    def init(): Spreadsheet = {
        val spreadsheet = Spreadsheet(project.dataframe("R"))
        spreadsheet
    }
    **/
    val A = ColumnRef(1, "A")
    val B = ColumnRef(2, "B")
    val C = ColumnRef(3, "C")
    
    "Schema serialization" >> {
        val project = MutableProject("Schema Serialization Test")
        project.load("test_data/r.csv", "R")
        val spreadsheet = Spreadsheet(project.dataframe("R"))
        val preSerialization = spreadsheet.schema
        //println(s"\n PREserialization: \n${preSerialization}")
        val jsonSchema = Json.toJson(preSerialization)
        val readableSchema = Json.prettyPrint(jsonSchema)
        //println(readableSchema)
        ///val schemaFromJson = Json.fromJson(jsonSchema)
        val schemaFromJson: JsResult[mutable.ArrayBuffer[OutputColumn]] = jsonSchema.validate[mutable.ArrayBuffer[OutputColumn]]
        var postSerialization: mutable.ArrayBuffer[OutputColumn] = null
        schemaFromJson match {
            case JsSuccess(s, _) => postSerialization = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        //println(s"\n POSTserialization: \n${postSerialization}")
        val jsonPostSerialization = Json.toJson(postSerialization)
        val readablePostSerialization = Json.prettyPrint(jsonPostSerialization)
        //println(readablePostSerialization)
        //This is such a bad way to test for equality but I will fix later
        readableSchema must_== readablePostSerialization
        ok
  }

  "Reference frame serialization" >> {
    val project = MutableProject("Reference frame Serialization Test")
    project.load("test_data/r.csv", "e")
    val spreadsheet = Spreadsheet(project.dataframe("e"))
    val preSerialization = spreadsheet.overlay.frame
    //println(s"\n Reference frame: \n${preSerialization}")
    val jsonRF = Json.toJson(preSerialization)
    val readableRF = Json.prettyPrint(jsonRF)
    //println(s"\n readable RF: \n${readableRF}")
    val rFFromJson: JsResult[ReferenceFrame] = jsonRF.validate[ReferenceFrame]
    var postSerialization: ReferenceFrame = null
    rFFromJson match {
        case JsSuccess(s, _) => postSerialization = s
        case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
    preSerialization must_== postSerialization
    ok
  }
  
  
  "Range Map serialization" >> {
      val project = MutableProject("Range Map Serialization Test")
      project.load("test_data/r.csv", "S")
      val spreadsheet = Spreadsheet(project.dataframe("S"))
      spreadsheet.overlay.addColumn(A)
      spreadsheet.overlay.addColumn(B)
      spreadsheet.overlay.update(A(1, 3), lit(1))
      spreadsheet.overlay.update(A(4, 7), lit(2))
      spreadsheet.overlay.update(A(8, 10), lit(3))
      val preSerialization = spreadsheet.overlay.dag.values.head
      //println(s"\n RangeMap: \n${preSerialization}")
      //println(s"\nRange Map data: \n${preSerialization.data}")
      
      val jsonRangeMap = Json.toJson(preSerialization)
      val readableRangeMap = Json.prettyPrint(jsonRangeMap)
      //println(readableRangeMap)
      val rMFromJson: JsResult[RangeMap[UpdateRule]] = jsonRangeMap.validate[RangeMap[UpdateRule]]
      var postSerialization: RangeMap[UpdateRule] = null
      rMFromJson match {
        case JsSuccess(s, _) => postSerialization = s
        case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
    }
    println(s"\n Pre serialization RangeMap: \n${preSerialization}")
    println(s"\n Post serialization RangeMap: \n${postSerialization}")
    preSerialization.toString() must_== postSerialization.toString()
    
    ok
  }
  
  
  "dag serialization" >> {
      lazy val project = MutableProject("DAG Serialization Test")
      project.load("test_data/r.csv", "Q")
      val spreadsheet = Spreadsheet(project.dataframe("Q"))
      spreadsheet.overlay.addColumn(A)
      spreadsheet.overlay.addColumn(B)
      spreadsheet.overlay.update(A(1, 3), lit(1))
      spreadsheet.overlay.update(A(4, 7), lit(2))
      spreadsheet.overlay.update(A(8, 10), lit(3))
      val preSerialization = spreadsheet.overlay.dag
      //println(s"\n preSerialization: \n${preSerialization}")
      val jsonDAG = Json.toJson(preSerialization)
      val readableDAG = Json.prettyPrint(jsonDAG)
      //println(readableDAG)
      val rFFromJson: JsResult[mutable.Map[ColumnRef, RangeMap[UpdateRule]]] = jsonDAG.validate[mutable.Map[ColumnRef, RangeMap[UpdateRule]]]
      var postSerialization: mutable.Map[ColumnRef, RangeMap[UpdateRule]] = null
      rFFromJson match {
        case JsSuccess(s, _) => postSerialization = s
        case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
    }
    val jsonPostSerialization = Json.toJson(postSerialization)
    val readablePostSerialization = Json.prettyPrint(jsonPostSerialization)
    println(s"\n preSerialization: \n${preSerialization}")
    println(s"\n postSerialization: \n${postSerialization}")
    //
    //val jsonCRef = Json.toJson(A)
    //println(s"\n json column ref: \n${jsonCRef}")
    //println(s"Immutable ${preSerialization.map(kv => (kv._1,kv._2.data.toSet)).toMap}")
    val immutableDAG = preSerialization.map(kv => (kv._1,kv._2.data.toSet)).toMap
    val immutableDAGPostSerialization = postSerialization.map(kv => (kv._1,kv._2.data.toSet)).toMap
    //jsonDAG must_== jsonPostSerialization
    immutableDAG must_== immutableDAGPostSerialization
    ok
  }



  //Unfinished test
  "spreadsheet serialization" >>
  {
    lazy val project = MutableProject("Spreadsheet Serialization Test")
    project.load("test_data/r.csv", "A")
    val spreadsheet = Spreadsheet(project.dataframe("A"))
    val constructor = SpreadsheetConstructor(Some(project.projectId), spreadsheet.overlay.dag, spreadsheet.overlay.frame, spreadsheet.schema)
    ok
  }



    /**
    project.load("test_data/r.csv", "R")
    val spreadsheet = init
    **/
    /**


    


    "Serialize subscriptions" >> {
        project.load("test_data/r.csv", "R")
        val spreadsheet = init

        val preSerialization = spreadsheet.overlay.subscriptions
        println(preSerialization)
        val jsonS = Json.toJson(preSerialization)
        val readableS = Json.prettyPrint(jsonS)
        println(readableS)
        val sFromJson: JsResult[RangeSet] = Json.fromJson[RangeSet](jsonS)
        var postSerialization: RangeSet = null
         sFromJson match {
            case JsSuccess(sub: RangeSet, path: JsPath) =>
                postSerialization = sub
            case e @ JsError(_) => {
                println("Seraializing subscriptions is broken")
            }
        }
        RangeSet.unapply(preSerialization).get must beEqualTo(RangeSet.unapply(preSerialization).get)
    }

**/
    /**
     * 
    "Serialize updates" >> {
        //implicit val updatesReads: Reads[Map[Long, UpdateRule]] = {
        
        implicit val updateReads
            
        project.load("test_data/r.csv", "e")
        val spreadsheet = Spreadsheet(project.dataframe("e"))
        val preSerialization = spreadsheet.overlay.updates
        println(preSerialization)
        val jsonS = Json.toJson(preSerialization)
        val readableS = Json.prettyPrint(jsonS)
        println(readableS)
        
        val sFromJson: JsResult[Map[Long, UpdateRule]] = Json.fromJson[Map[Long, UpdateRule]](jsonS)
        var postSerialization: Map[Long, UpdateRule] = null
         sFromJson match {
            case JsSuccess(u: Map[Long, UpdateRule], path: JsPath) =>
                postSerialization = u
            case e @ JsError(_) => {
                println("Seraializing updates is broken")
            }
         }
        preSerialization must beEqualTo(postSerialization)
         
    
    }

    **/
    
    /**

     "Misc" >> {
        
        println("Begin static insertions")

        //project.load("test_data/r.csv", "R")
        //val spreadsheet = init
        println("initialized spreadsheet")
        val s = spreadsheet.overlay.subscriptions
        val u = spreadsheet.overlay.updates
        //val d = spreadsheet.overlay.data
        val dag = spreadsheet.overlay.dag
        val t: Map[ColumnRef,Map[Long, TriggerSet]] = spreadsheet.overlay.triggers.map(kv => (kv._1,kv._2.toMap)).toMap
        val f = spreadsheet.overlay.frame
        println("about to spreadsheet construct")
        val spreadsheetConstructor = SpreadsheetConstructor(Some(project.projectId), s, u, dag, t, f)
        println("spreadsheet constructer constructed")
        val jsonSC = Json.toJson(spreadsheetConstructor)
        val readableSC = Json.prettyPrint(jsonSC)
        
        println("\n MASSIVE TEST:" + readableSC + "\n END MASSIVE TEST")

        
        val constructorFromJson: JsResult[SpreadsheetConstructor] = Json.fromJson[SpreadsheetConstructor](jsonSC)
        var postSerializationConstructor = spreadsheetConstructor
        constructorFromJson match {
            case JsSuccess(c: SpreadsheetConstructor, path: JsPath) =>
                postSerializationConstructor = c
            case e @ JsError(_) => {
                println(":/")
            }
        }
        
        postSerializationConstructor must beEqualTo(spreadsheetConstructor)


       
         
         spreadsheet.editCell(1, 2, Json.toJson("4"))
         for(outputcolumn <- spreadsheet.schema) {
             println(outputcolumn.ref)
         }
         println(spreadsheet.schema)
         println(spreadsheet.overlay.updates)
         for((id, rule) <- spreadsheet.overlay.updates){
             println(s"id: ${id} | ruleExpression ${rule.expression} | ruleFrame ${rule.frame}")
         }
         spreadsheet.editCell(1, 1, Json.toJson("=C1+C2"))
          for((id, rule) <- spreadsheet.overlay.updates){
             println(s"id: ${id} | ruleExpression ${rule.expression} | ruleFrame ${rule.frame}")
         }
         
         ok
     }
     **/

       /**
         val cr = ColumnRef(1)
         val jsonCR = Json.toJson(cr)
         val readableCR = Json.prettyPrint(jsonCR)
         print(readableCR)
         println(s"\n${cr.id}\n")

         val newJSResult = jsonCR.validate[ColumnRef]
         //val newCR: JsResult[ColumnRef] = newJSResult.asOpt[ColumnRef]

         println(s"\n${newJSResult.asOpt.get}\n")


        /// 
         **/

    

}

/**
object SpreadsheetSerialization{
  implicit val updatesReads = new Reads[Map[Long, UpdateRule]] {
    def reads(j: JsValue): JsResult[Map[Long, UpdateRule]] = {
        JsSuccess(j.as[Map[String, UpdateRule]].map{case (k, v) =>
            k.asInstanceOf[Long] -> v.asInstanceOf[UpdateRule]
        })
    }
    }

  implicit val updateWrites = new Writes[Map[Long, UpdateRule]] {
    def writes(u: Map[Long, UpdateRule]): JsValue = {
        Json.obj(u.map{case (k, v) =>
            val ret: (String, Json.JsValueWrapper) = k.toString() -> Json.toJson(v)
            ret
        }.toSeq:_*)
    }
  }
}
**/

