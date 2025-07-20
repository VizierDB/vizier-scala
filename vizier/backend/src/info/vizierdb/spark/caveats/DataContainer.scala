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
package info.vizierdb.spark.caveats

import play.api.libs.json._
import org.apache.spark.sql.types.{ StructType, StructField }
import info.vizierdb.spark.caveats.CaveatFormat._
import info.vizierdb.api.JsonResponse
import org.mimirdb.caveats.Caveat
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.spark.SparkPrimitive

case class DataContainer (
                  schema: Seq[StructField],
                  data: Seq[Seq[Any]],
                  prov: Seq[String],
                  colTaint: Seq[Seq[Boolean]],
                  rowTaint: Seq[Boolean],
                  reasons: Seq[Seq[Caveat]],
                  properties: Map[String,JsValue]
) extends JsonResponse[DataContainer]
{
  override def toString =
  {
    val rows = prov.zip(data)
                   .zip( (if(colTaint.isEmpty){ data.map { _.map { _ => false } } } else { colTaint }) )
                   .zip( (if(rowTaint.isEmpty){ data.map { _ => false } } else { rowTaint }) )
                   .map { 
      case (((rowid, row), colCaveats), rowCaveat) => 
        s"<$rowid>${if(rowCaveat){"*"}else{""}}" +: 
          row.map { case null => "null"; case x => x.toString }
             .zip(colCaveats)
             .map { 
               case (x, true) => x+"*" 
               case (x, false) => x
             }
    }
    val header = "ROWID" +: schema.map { _.name }

    val colWidth = 
      rows.foldLeft( header.map { _.length } ) { (width, row) => 
        width.zip(row).map { case (w, v) => math.max(w, v.length) }
      }

    val output = 
      (header +: rows).map { colWidth.zip(_).map { case (w, v) => 
        v.padTo(w, " ").mkString
      }.mkString(" | ")}

    (output.head +: ( 
      colWidth.map { "-" * _ }.mkString("-+-") +: output.tail
    )).mkString("\n")
  }
}

object DataContainer {
  implicit val format: Format[DataContainer] = Format(
    new Reads[DataContainer] {
      def reads(data: JsValue): JsResult[DataContainer] =
      {
        val parsed = data.as[Map[String,JsValue]]
        val schema = parsed("schema").as[Seq[StructField]]
        val sparkSchema = schema.map { _.dataType }
        JsSuccess(
          DataContainer(
            schema,
            parsed("data").as[Seq[Seq[JsValue]]].map { 
              _.zip(sparkSchema).map { case (dat, sch) => 
                SparkPrimitive.decode(dat, sch) 
              } 
            },
            parsed("prov").as[Seq[String]],
            parsed("colTaint").as[Seq[Seq[Boolean]]],
            parsed("rowTaint").as[Seq[Boolean]],
            if(parsed contains "reasons"){
              parsed("reasons").as[Seq[Seq[Caveat]]]
            } else { Seq.empty },
            parsed("properties").as[Map[String,JsValue]]
          )
        )
      }
    },
    new Writes[DataContainer] { 
      def writes(data: DataContainer): JsValue = {
        val sparkSchema = data.schema.map { _.dataType }
        Json.obj(
          "schema" -> data.schema,
          "data" -> data.data.map { row => 
            row.zip(sparkSchema).map { case (dat, sch) => 
              SparkPrimitive.encode(dat, sch) 
            }
          },
          "prov" -> data.prov,
          "colTaint" -> data.colTaint,
          "rowTaint" -> data.rowTaint,
          "reasons" -> data.reasons,
          "properties" -> data.properties
        )
      }
    }
  )
}