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
package info.vizierdb.api.spreadsheet

import play.api.libs.json._
import info.vizierdb.nativeTypes._
import scala.util.{ Try, Success, Failure }

sealed trait SpreadsheetCell
object SpreadsheetCell
{
  implicit val format = Format[SpreadsheetCell](
    new Reads[SpreadsheetCell] { def reads(j: JsValue) =
      (j \ "status").as[String] match {
        case "ready"   => JsSuccess(NormalValue((j \ "value").get, (j \ "caveat").as[Boolean]))
        case "running" => JsSuccess(ValueInProgress)
        case "error"   => JsSuccess(ErrorValue((j \ "message").as[String], (j \ "detail").as[String]))
        case _ => JsError()
      }
    },
    new Writes[SpreadsheetCell] { def writes(j: SpreadsheetCell) =
      j match {
        case NormalValue(value, caveat)  => Json.obj("status" -> "ready", "value" -> value, "caveat" -> caveat)
        case ValueInProgress             => Json.obj("status" -> "running")
        case ErrorValue(message, detail) => Json.obj("status" -> "error", "message" -> message, "detail" -> "detail")
      }
    }
  )

  def apply(v: Option[Try[Any]], dataType: CellDataType) =
  {
    v match {
      case None => ValueInProgress
      case Some(Success(v)) => NormalValue(jsonFromNative(v, dataType), false)
      case Some(Failure(err)) => ErrorValue(err)
    }
  }
}

case class NormalValue(value: JsValue, caveat: Boolean) extends SpreadsheetCell
{
  def as(dataType: CellDataType) = nativeFromJson(value, dataType)
}

case object ValueInProgress extends SpreadsheetCell

case class ErrorValue(message: String, detail: String) extends SpreadsheetCell
object ErrorValue
{
  def apply(err: Throwable): ErrorValue = 
    ErrorValue(err.getMessage(), err.getStackTrace().map { _.toString }.mkString("\n"))
}