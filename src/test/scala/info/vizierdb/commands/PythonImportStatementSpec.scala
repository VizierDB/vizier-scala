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
import org.apache.spark.sql.types._

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Message, DatasetMessage }
import info.vizierdb.MutableProject
import java.awt.image.BufferedImage


class PythonImportStatementSpec
    extends Specification
    with BeforeAll
{
    def beforeAll = SharedTestResources.init
    "Import Statement in a notebook" >> {
        val project = MutableProject("Importing a notebook")
        project.script("""
        |import pandas as pd
        |vizierdb.__setitem__("pd",pd)   
        """.stripMargin)

        val artifact_pandas = project.artifact("pd")
        artifact_pandas.t must beEqualTo(ArtifactType.FUNCTION)

        project.script("""
        |vizierdb.__getitem__("pd")
        |ser = pd.Series()
        |vizierdb.__setitem__("ser",ser)
           
        """.stripMargin)
        val artifact_ser = project.artifact("ser") 
        ok
    }

    
}