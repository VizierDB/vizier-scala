package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json._

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
    
    {
      val workflow = project.head
      val lastModule =
        DB readOnly { implicit s => 
          workflow.modulesInOrder
                  .last
        }
      lastModule.arguments.value("schema").as[Seq[JsValue]] must not beEmpty
    }

    project.append("data", "clone")(
      "dataset" -> "test_r",
      "name" -> "clone_r",
    )
    project.waitUntilReady

    project.append("data", "unload")(
      "dataset" -> "clone_r",
      "unloadFormat" -> "csv"
    )
    project.waitUntilReady

    project.append("data", "empty")(
      "name" -> "empty_ds"
    )
    project.waitUntilReady

    project.append("sql", "query")(
      "source" -> "SELECT * FROM empty_ds, test_r",
      "output_dataset" -> "query_result"
    )
    project.waitUntilReady

    ok
  }
}