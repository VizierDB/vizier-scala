package info.vizierdb.python

import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources

import info.vizierdb.python._

class IPyFlowSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Load iPyFlow" >> 
  {
    val script = PythonProcess.scriptResource("ipyflow")

    // make sure we're reading the ipyflow from the vendor directory
    script must contain("vendor") 
 
    PythonProcess.run(
      """from ipyflow.analysis import live_refs
        |print(live_refs)
        |print('Foo')
        |""".stripMargin
    ) must contain("Foo")
  }

  "Compute Dependencies" >>
  {
    PythonProcess.run(
      """from ipyflow.analysis.live_refs import ComputeLiveSymbolRefs
        |import ast
        |local_live = set()
        |local_dead = set()
        |refs = ComputeLiveSymbolRefs()
        |refs.push_attributes(live=local_live, dead=local_dead)
        |a = ast.parse('x = y')
        |refs.visit(a) 
        |print("-----------")
        |print(local_live)
        |print("-----------")
        |""".stripMargin
    ) must contain("shazbot")
  }


}