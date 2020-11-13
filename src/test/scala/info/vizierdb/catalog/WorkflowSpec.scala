package info.vizierdb.catalog

import scalikejdbc._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.viztrails.MutableProject


class WorkflowSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "enumerate workflows" >> {
    val project = MutableProject("Data Project")

    project.append("dummy", "print")(
      "value" -> "thingie1"
    )
    project.append("dummy", "print")(
      "value" -> "thingie2"
    )
    project.waitUntilReady

    val head = project.head

    val cellsAndModules = 
      DB.readOnly { implicit s => head.cellsAndModulesInOrder }

    println(cellsAndModules.map { x => x._1.toString + " / " + x._2.toString }.mkString("\n"))
    cellsAndModules must haveSize(2)
  }
}