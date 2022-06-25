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