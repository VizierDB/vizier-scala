package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json.{ Json, JsString }

import scalikejdbc._

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Project, Module }
import info.vizierdb.viztrails.{ Scheduler, MutableProject }

class DataCommandsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "load, unload, and query data" >> {
    val project = MutableProject("Data Project")

    project.append("data", "load")(
      "file" -> "test_data/r.csv",
      "name" -> "test_r",
      "loadFormat" -> "csv",
      "loadInferTypes" -> true,
      "loadDetectHeaders" -> true
    )
    project.waitUntilReady
    ok
  }
}