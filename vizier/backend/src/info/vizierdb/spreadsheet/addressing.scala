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
package info.vizierdb.spreadsheet

import org.apache.spark.sql.Column
import info.vizierdb.types._
import play.api.libs.json._

case class ColumnRef(id: Long)
{
  var label: String = null
  def apply(row: Long) = SingleCell(this, row)
  def apply(from: Long, to: Long) = ColumnRange(this, from, to)
  def offsetBy(by: Int) = OffsetCell(this, by)

  override def toString = Option(label).getOrElse { id.toString }
}
object ColumnRef
{
  def apply(id: Long, label: String): ColumnRef = 
  {
    val ret = ColumnRef(id)
    ret.label = label
    return ret
  }
}

/**
 * An 'lvalue'; a valid assignment **target**
 * 
 * May be:
 * - [[SingleCell]]: A reference to a sepcific cell
 * - [[ColumnRange]]: A reference to a range of cells being assigned to
 * - [[FullColumn]]: Like [[ColummRange]] but the entire column
 */
sealed trait LValue
{
  def column: ColumnRef
  def offsetLBy(offset: Long): LValue
}

object LValue
{
  implicit val format = Format[LValue](
    new Reads[LValue] {
      def reads(json: JsValue): JsResult[LValue] = 
        JsSuccess(
          (json \ "type").as[String] match {
            case "cell" => 
              SingleCell(
                ColumnRef( (json \ "col").as[Long] ),
                (json \ "row").as[Long]
              )
            case "range" =>
              ColumnRange(
                ColumnRef( (json \ "col").as[Long] ),
                (json \ "from").as[Long],
                (json \ "to").as[Long]
              )
            case "col" =>
              FullColumn(
                ColumnRef( (json \ "col").as[Long] ),
              )
          }
        )

    },
    new Writes[LValue] {
      def writes(o: LValue): JsValue = 
        o match {
          case SingleCell(col, row) => 
            Json.obj(
              "type" -> "cell",
              "col" -> col.id,
              "row" -> row
            )
          case ColumnRange(col, from, to) =>
            Json.obj(
              "type" -> "range",
              "col" -> col.id,
              "from" -> from,
              "to" -> to
            )
          case FullColumn(col) => 
            Json.obj(
              "type" -> "col",
              "col" -> col.id
            )
        }
    }
  )
}

/**
 * An 'rvalue'; a reference to a cell in the context of another cell being evaluated.
 * 
 * May be:
 * - [[SingleCell]]: A reference to a specific cell
 * - [[OffsetCell]]: A reference to a cell in another row offset by some number of rows in the current reference frame.
 */ 
sealed trait RValue
{ 
  def column: ColumnRef 

  def expr = RValueExpression(this)
  def ref = new Column(expr)
}
/**
 * A reference to a single cell identified by its absolute position.  May 
 * be used in an expression (i.e., an rvalue) or as an assignment target 
 * (i.e., an lvalue).
 */
case class SingleCell(column: ColumnRef, row: Long) extends LValue with RValue
{
  def offsetLBy(offset: Long): LValue = 
    copy(row = row + offset)
  override def toString =
    s"${column}[@$row]"
}
/**
 * A reference to a range of cells in a column.  May only be used as an
 * assignment target (i.e., an lvalue).
 */
case class ColumnRange(column: ColumnRef, from: Long, to: Long) extends LValue
{
  def offsetLBy(offset: Long): LValue = 
    copy(from = from + offset, to = to + offset)
  override def toString =
    s"${column}[$from-$to]"
}
/**
 * A reference to a range of cells in a column.  May only be used as an
 * assignment target (i.e., an lvalue).
 */
case class FullColumn(column: ColumnRef) extends LValue
{
  def offsetLBy(offset: Long): LValue = this
  override def toString =
    s"${column}[*]"
}
/**
 * A reference to a single cell, identified by a specific column and a
 * relative row offset.  May only be used in an expression (i.e., an 
 * rvalue).  The offset is specified relative to the cell for which
 * the expression is evaluated.
 */
case class OffsetCell(column: ColumnRef, rowOffset: Int) extends RValue
{
  override def toString =
    if(rowOffset == 0) { column.toString }
    else {
      s"${column}[${if(rowOffset >= 0){"+"}else{""}}$rowOffset]"
    }
}

/**
 * A reference to a specific row
 */
sealed trait RowReference

/**
 * A row from the source data (identified by its row position in the
 * source data).
 */
case class RowByIndex(idx: Long) extends RowReference

/**
 * A row that was inserted by the overlay.
 */
case class InsertedRow(insertId: Identifier, index: Int) extends RowReference