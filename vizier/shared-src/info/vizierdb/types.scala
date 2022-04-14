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

object types 
{
  type Identifier = Long
  type DatasetIdentifier = String
  type FileIdentifier = java.util.UUID
  type CellDataType = nativeTypes.CellDataType
  type RowIdentifier = String

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

    def merge(xs: Seq[T]): T =
    {
      xs.foreach { _ match {
        case CANCELLED => return CANCELLED
        case ERROR => return ERROR
        case RUNNING | STALE | WAITING => return RUNNING
        case _ => ()
      } }
      return DONE
    }

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
    val PARAMETER = Value(6, "Parameter")
    val VEGALITE  = Value(7, "VegaLite Chart")
    
    @deprecated("Use ArtifactType.VEGALITE instead of ArtifactType.CHART")
    val CHART     = Value(5, "Chart")

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

  object MessageType extends Enumeration
  {
    type T = Value

    val TEXT        = Value(1, "text/plain")
    val HTML        = Value(2, "text/html")
    val MARKDOWN    = Value(3, "text/markdown")
    val JAVASCRIPT  = Value(4, "text/javascript")
    val DATASET     = Value(5, "dataset/view")
    val VEGALITE    = Value(7, "chart/vegalite")
    val PNG_IMAGE   = Value(8, "image/png")
    // if you add a message here, update catalog/Message.scala as well

    @deprecated("Use MessageType.VEGALITE instead of MessageType.CHART")
    val CHART       = Value(6, "chart/view")

    def decode(name: String): T = 
    {
      name.toLowerCase match { 
        case "application/javascript" => JAVASCRIPT
        case x => withName(x) // fallback to Enumeration implementation
      }
    }
  }

  object MIME
  {
    @deprecated("Use MIME.VEGALITE instead of MIME.CHART")
    val CHART_VIEW    = "chart/view"
    val VEGALITE      = "chart/vegalite"
    val TEXT          = "text/plain"
    val HTML          = "text/html"
    val MARKDOWN      = "text/markdown"
    val DATASET_VIEW  = "dataset/view"
    val PYTHON        = "application/python"
    val JAVASCRIPT    = "text/javascript"
    val RAW           = "application/octet-stream"
    val JSON          = "text/json"
    val PNG           = "image/png"
    val DIRECTORY     = "inode/directory"
  }

  object DatasetFormat
  {
    type T = String
    val CSV         = "csv"
    val JSON        = "json"
    val PDF         = "mimir.exec.spark.datasource.pdf"
    val GSheet      = "mimir.exec.spark.datasource.google.spreadsheet"
    val XML         = "com.databricks.spark.xml"
    val Excel       = "com.crealytics.spark.excel"
    val JDBC        = "jdbc"
    val Text        = "text"
    val Parquet     = "parquet"
    val ORC         = "orc"
    val VizierLocal = "publish_local"

    val ALL = Seq[(String,String)](
      "CSV"               -> CSV,
      "JSON"              -> JSON,
      "PDF"               -> PDF,
      "Google Sheet"      -> GSheet,
      "XML"               -> XML,
      "Excel"             -> Excel,
      "JDBC Source"       -> JDBC,
      "Text"              -> Text,
      "Parquet"           -> Parquet,
      "ORC"               -> ORC,
      "Locally Published" -> VizierLocal,
    )
  }

  object DataSourceProtocol {
    type T = String

    val S3A                    = "s3a"
    val HTTP                   = "http"
    val HTTPS                  = "https"
  }

  object DataTypes
  {
    val BY_NAME = Seq[(String, String)](
      "String"          -> "string",
      "Real"            -> "real",
      "Float"           -> "float",
      "Large Float"     -> "double",
      "Bool"            -> "boolean",
      "Small Integer"   -> "short",
      "Integer"         -> "int",
      "Large Integer"   -> "long",
      "1 Byte"          -> "byte",
      "Date"            -> "date",
      "Date+Time"       -> "timestamp",
      "Geometry"        -> "geometry",
      "PNG Image"       -> "image/png"
    )
  }
}

