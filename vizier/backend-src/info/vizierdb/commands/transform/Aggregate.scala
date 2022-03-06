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
package info.vizierdb.commands.transform

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.types.ArtifactType
import info.vizierdb.catalog.Artifact
import info.vizierdb.viztrails.ProvenancePrediction

object Aggregate
  extends SQLTemplateCommand
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_GROUPBY = "group_by"
  val PARAM_AGGREGATES = "aggregates"
  val PARAM_COLUMN = "column"
  val PARAM_AGG_FN = "agg_fn"
  val PARAM_OUTPUT_COLUMN = "output_col"

  def name = "Aggregate Dataset"
  def templateParameters = Seq[Parameter](
    DatasetParameter(id = PARAM_DATASET, name = "Input Dataset"),
    ListParameter(id = PARAM_GROUPBY, name = "Group By", required = false, components = Seq(
      ColIdParameter(id = PARAM_COLUMN, name = "Column", required = true)
    )),
    ListParameter(id = PARAM_AGGREGATES, name = "Aggregate", required = false, components = Seq(
      ColIdParameter(id = PARAM_COLUMN, name = "Column", required = false),
      EnumerableParameter(id = PARAM_AGG_FN, name = "Function", required = true, values = EnumerableValue.withNames(
        "Count" -> "count",
        "Sum" -> "sum",
        "Average" -> "avg",
        "Min" -> "min",
        "Max" -> "max",
      )),
      StringParameter(id = PARAM_OUTPUT_COLUMN, name = "Output Name", required = false, default = Some(""))
    )),
  )

  def format(arguments: Arguments): String = 
  {
    val gbCols = arguments.getList(PARAM_GROUPBY).map { "["+_.get[Int](PARAM_COLUMN)+"]" }
    "SELECT "+(
      gbCols ++
      arguments.getList(PARAM_AGGREGATES).map { colArgs => 
        val fn = colArgs.get[String](PARAM_AGG_FN)
        val col = colArgs.getOpt[Int](PARAM_COLUMN) match {
          case None      => "*"
          case Some(col) => "["+col+"]"
        }
        val alias = colArgs.getOpt[String](PARAM_OUTPUT_COLUMN) match {
          case None => ""
          case Some(alias) => " AS "+alias
        }
        fn+"("+col+")"+alias
      }
    ).mkString(", ")+
    " FROM "+arguments.get[String](PARAM_DATASET)+
    (if(gbCols.isEmpty) { "" } else { 
      " GROUP BY "+gbCols.mkString(", ")
    })+(arguments.getOpt[String](PARAM_OUTPUT_DATASET) match {
      case None => ""
      case Some(dsname) => " INTO "+dsname
    })
  }

  def title(arguments: Arguments): String =
  {
    s"Aggregate ${arguments.get[String](PARAM_DATASET)}"
  }

  def query(arguments: Arguments, context: ExecutionContext): (Map[String, Artifact], String) =
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val dataset = context.artifact(datasetName) 
                         .getOrElse { throw new RuntimeException(s"Dataset $datasetName not found.")}
    if(dataset.t != ArtifactType.DATASET){
      throw new RuntimeException(s"$datasetName is not a dataset")
    }
    val datasetSchema = DB.autoCommit { implicit s => dataset.datasetSchema }
    def col(idx: Int) = s"`${datasetSchema(idx).name}`"
    def as(in: Option[String]) = in match { case None => "" 
                                            case Some(x) => s" AS `${x.replaceAll("[^a-zA-Z_0-9]", "")}`" }
    val gbCols = arguments.getList(PARAM_GROUPBY)
                          .map { _.get[Int](PARAM_COLUMN) }
                          .map { col(_) }
    val aggFns = arguments.getList(PARAM_AGGREGATES)
                          .map { args => (  args.get[String](PARAM_AGG_FN),
                                            args.getOpt[Int](PARAM_COLUMN),
                                            args.getOpt[String](PARAM_OUTPUT_COLUMN) ) }
                          .map { 
                            // explicitly check inputs to prevent SQL Injection attacks
                            case ("count", None, alias) => "count(*)"+as(alias)
                            case (fn@("sum" | "avg" | "count" | "min" | "max"),     
                                             Some(idx), alias) => s"$fn(${col(idx)})${as(alias)}"
                            case (fn,None,_) => throw new RuntimeException(s"Invalid aggregate $fn(*)")
                            case (fn,Some(idx),_) => throw new RuntimeException(s"Invalid aggregate $fn(${col(idx)})")

                          }

    val query = s"SELECT ${(gbCols++aggFns).mkString(",")} FROM __input__dataset__"+
                (if(gbCols.isEmpty) { "" } else { " GROUP BY "+gbCols.mkString(",")})+";"
    val deps = Map("__input__dataset__" -> dataset)
    return (deps, query)
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PARAM_DATASET))
      .definitelyWrites(
        arguments.getOpt[String](PARAM_OUTPUT_DATASET).getOrElse(DEFAULT_DS_NAME)
      )
      .andNothingElse
}
