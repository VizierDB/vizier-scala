package info.vizierdb.viztrails

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import org.squeryl.PrimitiveTypeMode._
import play.api.libs.json.{ Json, JsString }

import info.vizierdb.Vizier
import info.vizierdb.test.SharedTestResources

class ViztrailsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "create and recall projects" >> {
    val id = transaction {
      Project("Test Project").id
    }
    transaction {
      val project = Viztrails.projects.lookup(id).getOrElse { 
                      ko("newly created project doesn't exist"); null
                    }
      project.name must be equalTo("Test Project")

      val activeBranch = project.activeBranch
      project.branches.toSeq.map { _.id } must contain(activeBranch.id)

      val head = activeBranch.head
      head.action must beEqualTo(ActionType.CREATE)
      // head.length must beEqualTo(0)
      
      activeBranch.append(Module("dummy","print")(
        "value" -> "test"
      ))

      activeBranch.head.id must not(beEqualTo(head.id))
      activeBranch.head.action must beEqualTo(ActionType.APPEND)

      activeBranch.head.length must beEqualTo(1)
      val cells = activeBranch.head.cells.toSeq 
      cells must haveSize(1)
      cells(0).state must beEqualTo(ExecutionState.STALE)
      cells(0).module.packageId must beEqualTo("DATA")
    }
  }
}