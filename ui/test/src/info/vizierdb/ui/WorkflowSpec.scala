package info.vizierdb.ui

import utest._

object WorkflowSpec extends TestSuite with TestFixtures
{
  val tests = Tests 
  {
    test("Cancel edits") {

      val initialSize = modules.size 
      modules.appendTentative()

      assert(modules.last.isRight)
      val Right(editor) = modules.elements.last
      editor.cancelSelectCommand()

      assert(modules.size == initialSize)
    }

  }
}