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


class PythonSetItemSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  
  "Variables with primitive Data type" >> 
  {
    
    val project = MutableProject("Display Artifacts Test")

    project.script("""
      |x = 2
      |vizierdb.__setitem__("x",x)   
    """.stripMargin)
    
    val artifact_setitem = project.artifact("x")
    print(artifact_setitem.data)
    artifact_setitem.t must beEqualTo(ArtifactType.PARAMETER) 
  
  }
  


  "Variables with sequence data type" >> {

    val project = MutableProject("Display Artifacts Test - sequence variable")

    project.script("""
      |x_data = [2,3,4]
      |vizierdb.__setitem__("x_data",x_data)   
    """.stripMargin)
    
    val artifact_setitem = project.artifact("x_data")
    artifact_setitem.t must beEqualTo(ArtifactType.BLOB) 

  }

  "Variables with mapping data type" >> {

    val project = MutableProject("Display Artifacts Test - Mapping variable")

    project.script("""
      |x_dict = {1:'Nachiket'}
      |vizierdb.__setitem__("x_dict",x_dict)   
    """.stripMargin)
    
    val artifact_setitem = project.artifact("x_dict")
    artifact_setitem.t must beEqualTo(ArtifactType.BLOB) 
    ok
  }
  
  "Variables with function def" >> {

    val project = MutableProject("Display Artifacts Test - module")

    project.script("""
      |def add(x,y):
      | return x+y
      |vizierdb.__setitem__("add",add)   
    """.stripMargin)
    
    val artifact_setitem = project.artifact("add")
    //print(artifact_setitem.t)
    artifact_setitem.t must beEqualTo(ArtifactType.FUNCTION) 
    ok
  }

  "Variables with Pandas dataframe" >> {

    val project = MutableProject("Display Artifacts Test - Dataframe")

    project.script("""
      |import pandas as pd
      |df = pd.DataFrame([1,2,3])
      |vizierdb.__setitem__("df",df)   
    """.stripMargin)
    
    val artifactItem = project.artifact("df")
    //print(artifactItem.t)
    artifactItem.t must beEqualTo(ArtifactType.BLOB)     
    ok
  }

}
