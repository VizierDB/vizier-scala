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

import play.api.libs.json._

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.MutableProject
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.plot.SimpleChart

class SimpleChartSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init
  

  "Plot Nulls" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.append("plot", "chart")(
      "dataset" -> "r",
      "series" -> Seq(Map(
        "series_column" -> 2, 
      )),
      "xaxis" -> Map(
        "xaxis_column" -> 1
      ),
      "chart" -> Map()
    )
    project.waitUntilReadyAndThrowOnError
    ok
  }

  "Bar Chart" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.sql("SELECT a, count(*) as b, sum(c) as c FROM r GROUP BY a" -> "r")

    project.append("plot", "chart")(
      SimpleChart.PARAM_DATASET -> "r",
      SimpleChart.PARAM_NAME -> "bar",
      SimpleChart.PARAM_SERIES -> Seq(Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 1, 
      ),Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 2, 
      )),
      SimpleChart.PARAM_XAXIS -> Map(
        SimpleChart.PARAM_XAXIS_COLUMN -> 0
      ),
      SimpleChart.PARAM_CHART -> Map(
        SimpleChart.PARAM_CHART_TYPE -> SimpleChart.CHART_TYPE_BAR
      )
    )
    project.waitUntilReadyAndThrowOnError
    val data = project.artifact("bar").json
    println(data)
    ok
  }

  "Area Chart" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.sql("SELECT a, count(*) as b, sum(c) as c FROM r GROUP BY a" -> "r")

    project.append("plot", "chart")(
      SimpleChart.PARAM_DATASET -> "r",
      SimpleChart.PARAM_NAME -> "bar",
      SimpleChart.PARAM_SERIES -> Seq(Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 2, 
      ),Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 1, 
      )),
      SimpleChart.PARAM_XAXIS -> Map(
        SimpleChart.PARAM_XAXIS_COLUMN -> 0
      ),
      SimpleChart.PARAM_CHART -> Map(
        SimpleChart.PARAM_CHART_TYPE -> SimpleChart.CHART_TYPE_AREA
      )
    )
    project.waitUntilReadyAndThrowOnError
    val data = project.artifact("bar").json
    println(data)
    ok
  }

  "Scatterplot" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.sql("SELECT a, count(*) as b, sum(c) as c FROM r GROUP BY a" -> "r")

    project.append("plot", "chart")(
      SimpleChart.PARAM_DATASET -> "r",
      SimpleChart.PARAM_NAME -> "bar",
      SimpleChart.PARAM_SERIES -> Seq(Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 2, 
      ),Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 1, 
      )),
      SimpleChart.PARAM_XAXIS -> Map(
        SimpleChart.PARAM_XAXIS_COLUMN -> 0
      ),
      SimpleChart.PARAM_CHART -> Map(
        SimpleChart.PARAM_CHART_TYPE -> SimpleChart.CHART_TYPE_SCATTER
      )
    )
    project.waitUntilReadyAndThrowOnError
    val data = project.artifact("bar").json
    println(data)
    ok
  }

  "Line Plot" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.sql("SELECT a, count(*) as b, sum(c) as c FROM r GROUP BY a" -> "r")

    project.append("plot", "chart")(
      SimpleChart.PARAM_DATASET -> "r",
      SimpleChart.PARAM_NAME -> "bar",
      SimpleChart.PARAM_SERIES -> Seq(Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 2, 
      ),Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 1, 
      )),
      SimpleChart.PARAM_XAXIS -> Map(
        SimpleChart.PARAM_XAXIS_COLUMN -> 0
      ),
      SimpleChart.PARAM_CHART -> Map(
        SimpleChart.PARAM_CHART_TYPE -> SimpleChart.CHART_TYPE_LINE_NO_POINTS
      )
    )
    project.waitUntilReadyAndThrowOnError
    val data = project.artifact("bar").json
    println(data)
    ok
  }

  "LinesPoints Plot" >> {
    val project = MutableProject("Plot commands")
    project.load("test_data/r.csv", "r")
    project.sql("SELECT a, count(*) as b, sum(c) as c FROM r GROUP BY a" -> "r")

    project.append("plot", "chart")(
      SimpleChart.PARAM_DATASET -> "r",
      SimpleChart.PARAM_NAME -> "bar",
      SimpleChart.PARAM_SERIES -> Seq(Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 2, 
      ),Map(
        SimpleChart.PARAM_SERIES_COLUMN -> 1, 
      )),
      SimpleChart.PARAM_XAXIS -> Map(
        SimpleChart.PARAM_XAXIS_COLUMN -> 0
      ),
      SimpleChart.PARAM_CHART -> Map(
        SimpleChart.PARAM_CHART_TYPE -> SimpleChart.CHART_TYPE_LINE_POINTS
      )
    )
    project.waitUntilReadyAndThrowOnError
    val data = project.artifact("bar").json
    println(data)
    ok
  }
}