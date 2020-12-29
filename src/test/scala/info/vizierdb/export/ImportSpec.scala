package info.vizierdb.export

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import java.io.FileInputStream

import info.vizierdb.test.SharedTestResources

class ImportSpec
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init

  "Import NYC Causes of Death" >> {
    ImportProject(
      new FileInputStream("test_data/workflows/NYCCauseOfDeath.tar.gz"),
      execute = true
    )
    ok
  }
}