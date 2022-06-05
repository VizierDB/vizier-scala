package info.vizierdb.viztrails

import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.util.TimerUtils.time
import info.vizierdb.util.ExperimentalOptions
import info.vizierdb.commands.python.Python
import info.vizierdb.Vizier

class WorkflowSaturationRegression
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Recover from Saturated Queue" >>
  {
    val numThreads = 
      (Vizier.config.supervisorThreads().toInt + 3)

    val projects = 
      (1 until numThreads).map { i => MutableProject(s"Workflow Saturation-$i")}

    for(p <- projects){
      p.script(
        """from time import sleep
          |sleep(1)""".stripMargin, 
        waitForResult = false
      )
    }

    for(p <- projects)
    {
      p.waitUntilReadyAndThrowOnError
    }

    ok
  }
}
