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
import info.vizierdb.Vizier
import scala.collection.compat.immutable.LazyList
import info.vizierdb.catalog.Artifact

class SpreadsheetSerialization
    extends Specification
    with BeforeAll
{
      def spreadsheetEquals(a: Spreadsheet, b: Spreadsheet): Boolean = {
    var equals = true
    val aSchema = a.schema.map((OutputColumn.unapply(_))) 
    val bSchema = b.schema.map((OutputColumn.unapply(_)))

    val aUpdates = a.overlay.updates.toSeq
    val bUpdates = b.overlay.updates.toSeq
    
    val aDag = a.overlay.dag.map(kv => (kv._1,kv._2.data.toSet)).toMap
    val bDag = b.overlay.dag.map(kv => (kv._1,kv._2.data.toSet)).toMap

    val aData = a.overlay.data.map(kv => (kv._1,kv._2.toSet)).toMap
    val bData = b.overlay.data.map(kv => (kv._1,kv._2.toSet)).toMap

    val aTriggers = a.overlay.triggers.map(kv => (kv._1,kv._2.toSet)).toMap
    val bTriggers = b.overlay.triggers.map(kv => (kv._1,kv._2.toSet)).toMap

    val aFrame = a.overlay.frame
    val bFrame = b.overlay.frame

    if(aSchema != bSchema) {
      println("schemas are not equal")
      println(s"${aSchema}\n\u2260\n${bSchema}")
      equals = false
    }
    if(aUpdates != bUpdates) {
      println("updates are not equal")
      println(s"${aUpdates}\n\u2260\n${bUpdates}")
      equals = false
    }
    if(aDag != bDag) {
      println("dags are not equal")
      println(s"${aDag}\n\u2260\n${bDag}")
      equals = false
    }
    if(aData != bData) {
      println("data are not equal")
      println(s"${aData}\n\u2260\n${bData}")
      equals = false
    }
    if(aTriggers != bTriggers) {
      println("triggers are not equal")
      println(s"${aTriggers}\n\u2260\n${bTriggers}")
      equals = false
    }
    if(aFrame != bFrame) {
      println("frames are not equal")
      println(s"${aFrame}\n\u2260\n${bFrame}")
      equals = false
    }
    equals
  }

    
    def beforeAll = SharedTestResources.init

    
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val A = ColumnRef(1, "A")
    val B = ColumnRef(2, "B")
    val C = ColumnRef(3, "C")

    "Test everything" >> {
        //"Schema serialization" >> {
        {
        lazy val project = MutableProject("Schema Serialization Test")
        project.load("test_data/r.csv", "R")
        val spreadsheet = Spreadsheet(project.dataframe("R"))
        val preSerialization = spreadsheet.schema
        val jsonSchema = Json.toJson(preSerialization)
        val readableSchema = Json.prettyPrint(jsonSchema)
        //println(readableSchema)
        val schemaFromJson: JsResult[mutable.ArrayBuffer[OutputColumn]] = jsonSchema.validate[mutable.ArrayBuffer[OutputColumn]]
        var postSerialization: mutable.ArrayBuffer[OutputColumn] = null
        schemaFromJson match {
            case JsSuccess(s, _) => postSerialization = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        val jsonPostSerialization = Json.toJson(postSerialization)
        val readablePostSerialization = Json.prettyPrint(jsonPostSerialization)
        //println(readablePostSerialization)
        preSerialization.map((OutputColumn.unapply(_))) must_== postSerialization.map((OutputColumn.unapply(_)))
        }

        //"Reference frame serialization" >> {
        {
        lazy val project = MutableProject("Reference frame Serialization Test")
        project.load("test_data/r.csv", "e")
        val spreadsheet = Spreadsheet(project.dataframe("e"))
        val preSerialization = spreadsheet.overlay.frame
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
        }
  
        //"Range Map serialization" >> {
        {
        lazy val project = MutableProject("Range Map Serialization Test")
        project.load("test_data/r.csv", "S")
        val spreadsheet = Spreadsheet(project.dataframe("S"))
        spreadsheet.overlay.addColumn(A)
        spreadsheet.overlay.addColumn(B)
        spreadsheet.overlay.update(A(1, 3), lit(1))
        spreadsheet.overlay.update(A(4, 7), lit(2))
        spreadsheet.overlay.update(A(8, 10), lit(3))
        val preSerialization = spreadsheet.overlay.dag.values.head
        val jsonRangeMap = Json.toJson(preSerialization)
        val readableRangeMap = Json.prettyPrint(jsonRangeMap)
        //println(readableRangeMap)
        val rMFromJson: JsResult[RangeMap[UpdateRule]] = jsonRangeMap.validate[RangeMap[UpdateRule]]
        var postSerialization: RangeMap[UpdateRule] = null
        rMFromJson match {
            case JsSuccess(s, _) => postSerialization = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        preSerialization.data.toSet must_== postSerialization.data.toSet
        }
  
        //"dag serialization" >> {
        {
        lazy val project = MutableProject("DAG Serialization Test")
        project.load("test_data/r.csv", "Q")
        val spreadsheet = Spreadsheet(project.dataframe("Q"))
        spreadsheet.overlay.addColumn(A)
        spreadsheet.overlay.addColumn(B)
        spreadsheet.overlay.update(A(1, 3), lit(1))
        spreadsheet.overlay.update(A(4, 7), lit(2))
        spreadsheet.overlay.update(A(8, 10), lit(3))
        val preSerialization = spreadsheet.overlay.dag
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
        val immutableDAG = preSerialization.map(kv => (kv._1,kv._2.data.toSet)).toMap
        val immutableDAGPostSerialization = postSerialization.map(kv => (kv._1,kv._2.data.toSet)).toMap
        immutableDAG must_== immutableDAGPostSerialization
        }
    
        //"spreadsheet serialization" >> {
        {
        lazy val project = MutableProject("Spreadsheet serialization test")
        project.load("test_data/r.csv", "W")
        val preSerialization = Spreadsheet(project.dataframe("W"))
        preSerialization.overlay.addColumn(A)
        preSerialization.overlay.addColumn(B)
        preSerialization.overlay.update(A(1, 3), lit(1))
        preSerialization.overlay.update(A(4, 7), lit(2))
        preSerialization.overlay.update(A(8, 10), lit(3))
        val spreadsheetConstructor = SpreadsheetConstructor(Some(project.projectId), preSerialization.overlay.dag, preSerialization.overlay.frame, preSerialization.schema)
        val jsonConstructor = Json.toJson(spreadsheetConstructor)
        val readableConstructor = Json.prettyPrint(jsonConstructor)
        //println(readableConstructor)
        val constructorFromJson: JsResult[SpreadsheetConstructor] = jsonConstructor.validate[SpreadsheetConstructor]
        var cDeserialized: SpreadsheetConstructor = null
        var postSerialization: Spreadsheet = null
        constructorFromJson match {
            case JsSuccess(s, _) => cDeserialized = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        postSerialization = Spreadsheet(project.dataframe("W"))
        postSerialization.overlay.dag ++= cDeserialized.dag
        postSerialization.overlay.frame = cDeserialized.frame

        for((k,v) <- postSerialization.overlay.dag) {
            val rules = v.data.values.map(_._2)
            for (rule <- rules) postSerialization.overlay.updates.put(rule.id, rule)
        }
        spreadsheetEquals(preSerialization, postSerialization) must beTrue
        ok
        }

    }
}