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
package info.vizierdb

import play.api.libs.json._
import org.apache.spark.sql.execution.columnar.STRING

object types 
{
  type Identifier = Long
  type DatasetIdentifier = String
  type FileIdentifier = java.util.UUID

  object ActionType extends Enumeration
  {
    type T = Value

    val CREATE,
        APPEND,
        DELETE,
        INSERT,
        UPDATE,
        FREEZE = Value

    def decode(str: String): T = 
    {
      str.toLowerCase match {
        case "create" | "cre" => CREATE
        case "append" | "apd" => APPEND
        case "delete" | "del" => DELETE
        case "insert" | "ins" => INSERT
        case "update" | "upd" => UPDATE
        case "freeze" | "frz" => FREEZE
      }
    }

    def encode(t: T) = t.toString().toLowerCase()
  }

  object ExecutionState extends Enumeration
  {
    type T = Value

    // Note that WAITING and STALE states are primarily for the user's benefit
    // In general, we can compute these directly from the provenance.

    val DONE      = Value(1, "DONE")     /* The referenced execution is correct and up-to-date */
    val ERROR     = Value(2, "ERROR")    /* The cell or a cell preceding it is affected by a notebook 
                                            error */
    val WAITING   = Value(3, "WAITING")  /* The referenced execution follows a stale cell and *may* be 
                                            out-of-date, depending on the outcome of the stale cell  */
    // There existed a BLOCKED state (id=4) during development that was never
    // actually used.
    val STALE     = Value(5, "STALE")    /* The referenced execution is incorrect for this workflow and 
                                            needs to be recomputed */
    val CANCELLED = Value(6, "CANCELLED")/* Execution of the cell or a cell preceding it was 
                                            cancelled (equivalent to ERROR) */
    val FROZEN    = Value(7, "FROZEN")   /* The cell has temporarily been removed from the workflow */
    val RUNNING   = Value(8, "RUNNING")  /* The referenced execution is incorrect for this workflow and 
                                            is currently being recomputed */

    def translateToClassicVizier(state: T): Int = 
    {
      state match {
        case DONE => 4      // MODULE_SUCCESS
        case ERROR => 3     // MODULE_ERROR
        case WAITING => 0   // MODULE_PENDING
        case STALE => 1     // MODULE_RUNNING
        case RUNNING => 1   // MODULE_RUNNING
        case CANCELLED => 2 // MODULE_CANCELLED
        case FROZEN => 5    // MODULE_SUCCESS
      }
    }

    val PENDING_STATES: Set[T] = Set(
      WAITING,
      STALE,
      RUNNING,
    )

    val PROVENANCE_VALID_STATES: Set[T] = Set(
      DONE,
      WAITING,
      STALE,
      CANCELLED,
      FROZEN
    )
    val PROVENANCE_NOT_VALID_STATES: Set[T] = 
      this.values.toSet -- PROVENANCE_VALID_STATES

    implicit val format = Format[T](
      new Reads[T]{
        def reads(j: JsValue): JsResult[T] = 
          JsSuccess(apply(j.as[Int]))
      },
      new Writes[T]{
        def writes(s: T): JsValue =
          JsNumber(s.id)
      }
    )
  }

  object ArtifactType extends Enumeration
  {
    type T = Value

    val DATASET   = Value(1, "Dataset")
    val FUNCTION  = Value(2, "Function")
    val BLOB      = Value(3, "Blob")
    val FILE      = Value(4, "File")
    val CHART     = Value(5, "Chart")
    val PARAMETER = Value(6, "Parameter")

    implicit val format = Format[T](
      new Reads[T]{
        def reads(j: JsValue): JsResult[T] =
          JsSuccess(apply(j.as[Int]))
      },
      new Writes[T]{
        def writes(s: T): JsValue =
          JsNumber(s.id)
      }
    )
  }

  object StreamType extends Enumeration
  {
    type T = Value

    val STDOUT = Value(1, "stdout")
    val STDERR = Value(2, "stderr")

    implicit val format = Format[T](
      new Reads[T]{
        def reads(j: JsValue): JsResult[T] =
          JsSuccess(apply(j.as[Int]))
      },
      new Writes[T]{
        def writes(s: T): JsValue =
          JsNumber(s.id)
      }
    )
  }

  object MIME
  {
    val CHART_VIEW    = "chart/view"
    val TEXT          = "text/plain"
    val HTML          = "text/html"
    val MARKDOWN      = "text/markdown"
    val DATASET_VIEW  = "dataset/view"
    val PYTHON        = "application/python"
    val JAVASCRIPT    = "text/javascript"
    val JSON          = "text/json"
    val PNG           = "image/png"
  }

  object DATATYPE extends Enumeration
  {
    type T = Value

    val INT      = Value(1, "int")
    val SHORT    = Value(2, "short")
    val LONG     = Value(3, "long")
    val REAL     = Value(4, "real")
    val VARCHAR  = Value(5, "varchar")
    val DATE     = Value(6, "date")
    val DATETIME = Value(7, "datetime")
    val BINARY   = Value(8, "binary")
    val IMAGE    = Value(9, "image/png")

    def fromSpark(t: org.apache.spark.sql.types.DataType): T =
    {
      t match {
        case org.apache.spark.sql.types.IntegerType => INT
        case org.apache.spark.sql.types.ShortType => SHORT
        case org.apache.spark.sql.types.LongType => LONG
        case org.apache.spark.sql.types.FloatType => REAL
        case org.apache.spark.sql.types.DoubleType => REAL
        case org.apache.spark.sql.types.StringType => VARCHAR
        case org.apache.spark.sql.types.DateType => DATE
        case org.apache.spark.sql.types.TimestampType => DATETIME
        case org.apache.spark.sql.types.BinaryType => BINARY
        case org.apache.spark.sql.types.ImageUDT => IMAGE
        case _ => VARCHAR
      }
    }
  }
}

