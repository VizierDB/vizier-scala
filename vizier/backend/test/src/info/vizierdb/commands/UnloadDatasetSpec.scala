package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.MutableProject
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.data.UnloadDataset
import info.vizierdb.types
import java.io.File
import info.vizierdb.util.FileUtils
import org.specs2.matcher.FileMatchers

class UnloadDatasetSpec
  extends Specification
  with BeforeAll
  with FileMatchers
{
  def beforeAll = SharedTestResources.init

  lazy val project = MutableProject("Vizual Commands Test")

  sequential

  "CSV files should be unloaded as a single file" >> 
  {
  	project.load("test_data/r.csv", name = "r")

  	val tempdir = File.createTempFile("unload_test_", ".csv")
  	tempdir.delete()

  	project.append("data", "unload")(
  		UnloadDataset.PARAM_DATASET -> "r",
  		UnloadDataset.PARAM_FORMAT -> types.DatasetFormat.CSV,
  		UnloadDataset.PARAM_URL -> tempdir.getAbsoluteFile().toString()
  	)
  	project.waitUntilReadyAndThrowOnError

  	tempdir must beAFile

  	FileUtils.recursiveDelete(tempdir)

  	ok
  }
}