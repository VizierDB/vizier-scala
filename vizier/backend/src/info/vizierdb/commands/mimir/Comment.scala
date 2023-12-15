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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.when
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.util.StringUtils
import org.mimirdb.caveats.implicits._
import info.vizierdb.types._

object Comment
  extends LensCommand
  with UntrainedLens
{ 
  val PARAM_COMMENTS = "comments"
  val PARAM_COLUMN = "expression"
  val PARAM_ROW = "rowid"
  val PARAM_COMMENT = "comment"

  def name: String = "Comment"
  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_COMMENTS, name = "Comments", components = Seq(
      ColIdParameter(id = PARAM_COLUMN, name = "Column"),
      RowIdParameter(id = PARAM_ROW, name = "Row"),
      StringParameter(id = PARAM_COMMENT, name = "Comment")
    ))
  )

  def format(arguments: Arguments): String =
  {
    val dataset = arguments.pretty(PARAM_DATASET)
    val columns = arguments.getList(PARAM_COMMENTS)
                           .map { _.get[Int](PARAM_ROW) }
                           .map { dataset + "." + _ }
    s"Apply comments to ${StringUtils.oxfordComma(columns)}"
  }

  def title(arguments: Arguments): String =
    s"Comment ${arguments.pretty(PARAM_DATASET)}"


  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    val comments: Map[Int, Seq[(String, String)]] = 
      arguments.getList(PARAM_COMMENTS).map { comment =>
        comment.get[Int](PARAM_COLUMN) -> (
          comment.get[String](PARAM_ROW),
          comment.get[String](PARAM_COMMENT)
        )
      }.groupBy(_._1)
       .mapValues { _.map { _._2 } }

    AnnotateWithRowIds.withRowId(df) { df => 
      df.select(
        df.schema.fieldNames.zipWithIndex.map { case (col, idx) =>
          {
            val rowIdCol = df(AnnotateWithRowIds.ATTRIBUTE)
            comments.getOrElse(idx, Seq.empty)
                    .foldLeft(df(col)) { case (expr, (row, comment)) =>
                      expr.caveatIf(
                        comment,
                        rowIdCol.eqNullSafe(row)
                      )
                    }
          }
        }:_*
      )
    }
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
}

