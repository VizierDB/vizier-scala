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
package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json._
import java.io.File

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Project, Module }
import info.vizierdb.viztrails.{ Scheduler, MutableProject }

import info.vizierdb.commands.transform._

class TransformCommandsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "aggregate datasets" >> {
    val project = MutableProject("Aggregate Test")
    project.load("test_data/r.csv","r")
    project.append("transform", "aggregate")(
      AggregateDataset.PARAM_DATASET -> "r",
      AggregateDataset.PARAM_GROUPBY -> Seq(2),
      AggregateDataset.PARAM_OUTPUT_DATASET -> "s",
      AggregateDataset.PARAM_AGGREGATES -> Seq(
        Map(
          AggregateDataset.PARAM_AGG_FN -> "count",
          AggregateDataset.PARAM_OUTPUT_COLUMN -> "biz",
        ),
        Map(
          AggregateDataset.PARAM_COLUMN -> 0,
          AggregateDataset.PARAM_AGG_FN -> "sum",
          AggregateDataset.PARAM_OUTPUT_COLUMN -> "foo",
        ),
        Map(
          AggregateDataset.PARAM_COLUMN -> 1,
          AggregateDataset.PARAM_AGG_FN -> "max",
          AggregateDataset.PARAM_OUTPUT_COLUMN -> "bar",
        ),
      )
    )
    project.waitUntilReadyAndThrowOnError
    val df = project.dataframe("s")
    df.schema.map { _.name } must beEqualTo(Seq("C", "biz", "foo", "bar"))
    df.collect must haveSize(5)
  }

  "filter datasets" >> {
    val project = MutableProject("Filter Test")
    project.load("test_data/r.csv","r")
    project.append("transform", "filter")(
      FilterDataset.PARAM_DATASET -> "r",
      FilterDataset.PARAM_OUTPUT_DATASET -> "s",
      FilterDataset.PARAM_FILTER -> "cast(a as int) > 1"
    )
    project.waitUntilReadyAndThrowOnError
    val df = project.dataframe("s")
    val data = df.collect.map { _.getString(1) }.toSeq
    data must not contain("1")
    data must haveSize(3)
  }

}