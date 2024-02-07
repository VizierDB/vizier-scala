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
package src.info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.MutableProject
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.plot.LineChart
import info.vizierdb.artifacts.VegaChart
import info.vizierdb.commands.plot.ScatterPlot
import info.vizierdb.commands.plot.BarChart
import info.vizierdb.artifacts.VegaRegressionMethod

class ChartSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init
  lazy val project = 
    {
      val project = MutableProject("General plotting")

      project.load("test_data/r.csv", "r")

      /* return */ project
    }

  lazy val rCount = 
    project.dataframe("r").count().toInt
  lazy val rCountOfAs = 
    project.dataframe("r").select("a").distinct().count().toInt

  sequential
  
  "Line Chart" >>
  {
    project.append("plot", "line-chart")(
      LineChart.PARAM_SERIES -> Seq(
        Map(
          LineChart.PARAM_DATASET -> "r",
          LineChart.PARAM_X -> 0,
          LineChart.PARAM_Y -> 1,
        )
      ),
      LineChart.PARAM_ARTIFACT -> "line"
    )
    project.waitUntilReadyAndThrowOnError
    val chart =
      project.artifact("line").json.as[VegaChart]
    chart.data(0).name must beEqualTo("r")
    chart.data(0).values must beSome
    chart.data(0).values.get must haveSize(rCount)
  }
  
  "Scatter Plot" >>
  {
    project.append("plot", "scatterplot")(
      ScatterPlot.PARAM_SERIES -> Seq(
        Map(
          ScatterPlot.PARAM_DATASET -> "r",
          ScatterPlot.PARAM_X -> 0,
          ScatterPlot.PARAM_Y -> 1,
          ScatterPlot.PARAM_REGRESSION -> VegaRegressionMethod.Linear.key
        )
      ),
      ScatterPlot.PARAM_ARTIFACT -> "scatter"
    )
    project.waitUntilReadyAndThrowOnError
    val chart =
      project.artifact("scatter").json.as[VegaChart]
    chart.data(0).name must beEqualTo("r")
    chart.data(0).values must beSome
    chart.data(0).values.get must haveSize(rCount)
    chart.data(1).values must beNone
  }
  
  "Bar Chart" >>
  {
    project.append("plot", "bar-chart")(
      BarChart.PARAM_SERIES -> Seq(
        Map(
          BarChart.PARAM_DATASET -> "r",
          BarChart.PARAM_X -> 0,
          BarChart.PARAM_Y -> 1,
        )
      ),
      BarChart.PARAM_ARTIFACT -> "bar"
    )
    project.waitUntilReadyAndThrowOnError
    val chart =
      project.artifact("bar").json.as[VegaChart]
    chart.data(0).name must beEqualTo("r")
    chart.data(0).values must beSome
    chart.data(0).values.get must haveSize(rCountOfAs)
  }
  
  "CDF" >>
  {
    project.append("plot", "cdf")(
      BarChart.PARAM_SERIES -> Seq(
        Map(
          BarChart.PARAM_DATASET -> "r",
          BarChart.PARAM_X -> 0,
        )
      ),
      BarChart.PARAM_ARTIFACT -> "cdf"
    )
    project.waitUntilReadyAndThrowOnError
    val chart =
      project.artifact("cdf").json.as[VegaChart]
    chart.data(0).name must beEqualTo("r")
    chart.data(0).values must beSome
    chart.data(0).values.get must haveSize(rCount)
  }

}