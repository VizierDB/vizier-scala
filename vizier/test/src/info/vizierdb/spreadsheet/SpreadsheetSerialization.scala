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
    }
}