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
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.MutableProject 
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
      Aggregate.PARAM_DATASET -> "r",
      Aggregate.PARAM_GROUPBY -> Seq(2),
      Aggregate.PARAM_OUTPUT_DATASET -> "s",
      Aggregate.PARAM_AGGREGATES -> Seq(
        Map(
          Aggregate.PARAM_AGG_FN -> "count",
          Aggregate.PARAM_OUTPUT_COLUMN -> "biz",
        ),
        Map(
          Aggregate.PARAM_COLUMN -> 0,
          Aggregate.PARAM_AGG_FN -> "sum",
          Aggregate.PARAM_OUTPUT_COLUMN -> "foo",
        ),
        Map(
          Aggregate.PARAM_COLUMN -> 1,
          Aggregate.PARAM_AGG_FN -> "max",
          Aggregate.PARAM_OUTPUT_COLUMN -> "bar",
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
      Filter.PARAM_DATASET -> "r",
      Filter.PARAM_OUTPUT_DATASET -> "s",
      Filter.PARAM_FILTER -> "cast(a as int) > 1"
    )
    project.waitUntilReadyAndThrowOnError
    val df = project.datasetData("s")
    val data = df.data.map { _(1) }
    data must not contain(1)
    data must haveSize(3)
  }

  "split datasets" >> {
    val project = MutableProject("Split Test")
    project.load("test_data/r.csv","r")
    project.append("transform", "split")(
      SplitDataset.PARAM_DATASET -> "r",
      SplitDataset.PARAM_PARTITIONS -> Seq(
        Map(
          SplitDataset.PARAM_CONDITION -> "cast(a as int) >= 2",
          SplitDataset.PARAM_OUTPUT -> "s"
        )
      )
    )
    project.append("transform", "split")(
      SplitDataset.PARAM_DATASET -> "r",
      SplitDataset.PARAM_PARTITIONS -> Seq(
        Map(
          SplitDataset.PARAM_CONDITION -> "cast(a as int) >= 2",
          SplitDataset.PARAM_OUTPUT -> "t",
        )
      ),
      SplitDataset.PARAM_OTHERWISE -> "u"
    )
    project.waitUntilReadyAndThrowOnError

    {
      val data = project.dataframe("s").collect().toSeq
      data.map { _.getString(0) } must not contain(1.toString)
      data must haveSize(3)    
    }
    
    {
      val data = project.dataframe("t").collect().toSeq
      data.map { _.getString(0) } must not contain(1.toString)
      data.map { _.getString(0) } must contain(2.toString)
      data must haveSize(3)    
    }
    
    {
      val data = project.dataframe("u").collect().toSeq
      data.map { _.getString(0) } must contain(exactly(1.toString, 1.toString, 1.toString, 1.toString))
    }
  }

}
