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

import scalikejdbc.DB
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types._
import info.vizierdb.spark.vizual._

class VizualCommandSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val project = MutableProject("Vizual Commands Test")

  "UpdateCell Like/Join Regression" >> 
  {

    project.load("test_data/explosions.csv", "R")
    // project.show("R")
    
    val rowids = project.datasetData("R").prov
    
    project.sql("""
      SELECT A, explode(split(B, '/')) AS grade FROM R
    """ -> "S")
    // project.show("S")

    project.vizual("R", 
      (
        InsertColumn(Some(2), "C", None)
        +: rowids.zipWithIndex.map { case (rowid, idx) => 
          UpdateCell(2, Some(RowsById(Set(rowid))), Some(JsString(s"Row ${idx+1}")), None)
        }
      ):_*
    )
    // project.show("R")

    project.sql("""
      SELECT S.A, S.grade, R.C
      FROM S, R
      WHERE S.A = R.A
       AND S.A NOT LIKE 'g%'
      ORDER BY S.grade
    """ -> "S")
    // project.show("S")

    project.dataframe("S")
           .collect
           .filter { _.isNullAt(2) } must beEmpty
    
  }
}

