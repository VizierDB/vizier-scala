package info.vizierdb.commands

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.apache.spark.sql.types.IntegerType
import info.vizierdb.Vizier

class LoadDatasetSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  sequential

  "Spark should infer schemas" >> {
    val df = Vizier.sparkSession.read
                   .option("header", "true")
                   .option("inferSchema", "true")
                   .csv("test_data/r.csv")
    df.schema.fields(0).name must beEqualTo("A")
    df.schema.fields(0).dataType must beEqualTo(IntegerType)
  }

  "Load Dataset should autodetect schemas" >> {
    val project = MutableProject("Load Dataset")
    project.load("test_data/r.csv", name = "r")
    val df = project.dataframe("r")
    df.schema.fields(0).dataType must beEqualTo(IntegerType)
    ok
  }
}