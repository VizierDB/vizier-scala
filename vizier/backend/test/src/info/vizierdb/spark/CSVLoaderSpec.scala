package info.vizierdb.spark

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import scala.collection.mutable
import info.vizierdb.commands.jvmScript._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.commands.FileArgument
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.IntegerType
import info.vizierdb.spark.load.LoadSparkCSV
import info.vizierdb.Vizier

class CSVLoaderSpec
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init()

  "Load CSV" >> 
  {
    val l = load.LoadSparkCSV(
      url = FileArgument(url = Some("test_data/r.csv")),
      schema = Seq(
        StructField("A", IntegerType),
        StructField("B", IntegerType),
        StructField("C", IntegerType),
      ),
      projectId = 0,
      contextText = Some("R"),
      skipHeader = true,
    )

    l.construct(_ => ???)
     .take(1)
     .map { r => (r.getInt(0), r.getInt(1), r.getInt(2)) } 
     .head must beEqualTo( (1, 2, 3) )
  }

  "Detect Schemas and Headers" >> 
  {
    val l = LoadSparkCSV.infer(
      url = FileArgument(url = Some("test_data/r.csv")),
      projectId = 0,
      "R",
      header = None,
    )

    l.schema must haveSize(3)
    l.schema(0).name must beEqualTo("A")
    l.schema(0).dataType must beEqualTo(IntegerType)
    l.schema(1).name must beEqualTo("B")
    l.schema(1).dataType must beEqualTo(IntegerType)
    l.schema(1).nullable must beTrue
    l.schema(2).name must beEqualTo("C")
    l.schema(2).dataType must beEqualTo(IntegerType)


  }
}