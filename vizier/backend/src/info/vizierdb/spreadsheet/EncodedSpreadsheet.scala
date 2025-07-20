/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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

import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.DataFrame
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer
import info.vizierdb.commands.data.EmptyDataset
import org.apache.spark.sql.types.StringType
import play.api.libs.json._

case class EncodedSpreadsheet(
  executor: String,
  schema: Seq[OutputColumn],
  updates: Seq[(UpdatePattern, Seq[LValue])],
  rows: SourceReferenceMap
)
{
  private def populate(spreadsheet: Spreadsheet) =
  {
    spreadsheet.executor
               .sourceRowMapping
               .replace(rows)
    spreadsheet.executor.loadUpdates(updates)
  }

  def rebuildFromDataframe(df: DataFrame)(implicit ec: ExecutionContext): Spreadsheet =
  {
    val cache = Spreadsheet.buildCache(df)
    val spreadsheet = new Spreadsheet(
      new CachedSource(
        df.schema.fields,
        cache,
        Future { df.count() }
      ),
      ArrayBuffer(schema:_*)
    )
    populate(spreadsheet)
    cache.selectForInvalidation = spreadsheet.pickCachePageToDiscard
    /* return */ spreadsheet
  }

  def rebuildFromEmpty(implicit ec: ExecutionContext): Spreadsheet =
  {
    val spreadsheet = 
      new Spreadsheet(
        new InlineSource(
          schema = Array(),
          data = Array()
        ),
        ArrayBuffer(schema:_*)
      )
    populate(spreadsheet)
    /* return */ spreadsheet
  }

  def refForColumn(id: Long): ColumnRef =
    schema.find { _.id == id }.get.ref

  def rebindVariables = 
    copy(
      updates = updates.map { case (pattern, targets) =>
        (pattern.copy(
          expression = 
            Spreadsheet.bindVariables(pattern.expression, schema)
        ), targets.map { 
          case SingleCell(col, row) =>
            SingleCell(refForColumn(col.id), row)
          case ColumnRange(col, from, to) =>
            ColumnRange(refForColumn(col.id), from, to)
          case FullColumn(col) =>
            FullColumn(refForColumn(col.id))
        })
      }
    )

  def updateMaps: Map[ColumnRef, (RangeMap[UpdatePattern], Option[UpdatePattern])] =
  {
    updates.flatMap { case (pattern, lvalues) => lvalues.map { (_, pattern) } }
           .groupBy { _._1.column }
           .mapValues { _.foldLeft( (new RangeMap[UpdatePattern], None:Option[UpdatePattern]) ) {
              case ( (patternMap, default), (lvalue, pattern) ) =>
                lvalue match {
                  case SingleCell(_, row) => 
                    patternMap.insert(row, pattern)
                    (patternMap, default)
                  case ColumnRange(_, from, to) =>
                    patternMap.insert(from, to, pattern)
                    (patternMap, default)
                  case FullColumn(_) =>
                    (patternMap, Some(pattern))
                }
           } }
  }
}

object EncodedSpreadsheet
{
  // def format: Format[EncodedSpreadsheet] = Json.format

  def fromSpreadsheet(spreadsheet: Spreadsheet): EncodedSpreadsheet =
  {
    EncodedSpreadsheet(
      executor = "single_row",
      schema = spreadsheet.schema.toSeq,
      updates = 
        spreadsheet.executor.updates
                   .flatMap { case (column, (updates, default)) =>
                      updates.iterator.map { case (low, high, update) =>
                        (update, ColumnRange(column, low, high))
                      } ++ default.map { update => 
                        (update, FullColumn(column))
                      }
                   }
                   .groupBy { _._1.id }
                   .map { case (id, updates) => 
                     (
                        updates.head._1,
                        updates.map { _._2 }.toSeq
                     )
                   }
                   .toSeq,
      rows = spreadsheet.executor.sourceRowMapping
    )
  }


  private type UpdateSet = (UpdatePattern, Seq[LValue])

  private implicit val updateSetFormat = Format[UpdateSet](
    new Reads[UpdateSet] {
      def reads(json: JsValue): JsResult[UpdateSet] = 
        JsSuccess( (
          (json \ "pattern").as[UpdatePattern],
          (json \ "targets").as[Seq[LValue]]
        ) )
    },
    new Writes[UpdateSet] {
      def writes(o: UpdateSet): JsValue = 
        Json.obj(
          "pattern" -> o._1,
          "targets" -> o._2
        )
    }
  )

  val defaultReads = Json.reads[EncodedSpreadsheet]
  val defaultWrites = Json.writes[EncodedSpreadsheet]

  implicit val format = Format[EncodedSpreadsheet](
    new Reads[EncodedSpreadsheet] {
      def reads(json: JsValue): JsResult[EncodedSpreadsheet] = 
        defaultReads.reads(json).map { _.rebindVariables }
    },
    defaultWrites
    // new Writes[EncodedSpreadsheet] {
    //   def writes(o: EncodedSpreadsheet): JsValue = 
    //     defaultWrites.writes(o.rebindVariables)
    // }
  )
}