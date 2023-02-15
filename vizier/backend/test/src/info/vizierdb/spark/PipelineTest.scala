package info.vizierdb.spark

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import scala.collection.mutable
import info.vizierdb.commands.jvmScript._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types.IntegerType

class PipelineTest
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init()

  "Create and save a simple pipeline" >>
  {
    val project = MutableProject("pipeline test")

    project.load(
      "test_data/r.csv",
      "r",
      schema = Seq(
        "A" -> IntegerType,
        "B" -> IntegerType,
        "C" -> IntegerType,
      )
    )

    project.script(
      """val a = vizierdb.createPipeline("r")(
        |  new org.apache.spark.ml.feature.OneHotEncoder()
        |    .setInputCols(Array("A"))
        |    .setOutputCols(Array("D"))
        |)
        |vizierdb.displayDataset("r")
        |""".stripMargin, 
      language = "scala"
    )

    project.dataframe("r").show()
    ok
  }
}