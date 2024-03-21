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
package info.vizierdb.spark

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.api.GetArtifact
import java.io.ByteArrayOutputStream

class GeometryRegressions
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init()

  // https://github.com/VizierDB/vizier-scala/issues/138
  "Downloading Geometry" >> 
  {
    val project = MutableProject("Geometry Export")

    println("Loading dataset")

    project.load("test_data/spatial_test.csv", name = "spatial", inferTypes = false)
    project.sql("SELECT ST_GeomFromWKT(the_geom) AS geom, globalid FROM spatial" -> "spatial")

    project.waitUntilReadyAndThrowOnError

    val response = 
      GetArtifact.CSV(project.projectId, project.artifact("spatial").id)

    val bytes = 
      new ByteArrayOutputStream()
    response.write(bytes)

    // print(new String(bytes.toByteArray()))

    bytes.toByteArray.size must beGreaterThan(0)
  }
}