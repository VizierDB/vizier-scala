package info.vizierdb.ui

import rx._
import utest._
import info.vizierdb.ui.components.{ TentativeEdits, Module }
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.test._
import info.vizierdb.types._
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.ui.components.StaticWorkflow


object TentativeEditsSpec extends TestSuite with TestFixtures
{
  val base = RxBuffer[Module]()
  val edits = new TentativeEdits(base, null, null)



  val tests = Tests 
  {
    test("Inserts into buffer go where they're needed") {
      implicit val ctx = Ctx.Owner.safe() 

      base.append(
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 1,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(0)
        ))
      )
      base.append(
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 2,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(1)
        ))
      )
      base.insert(
        1,
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 3,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(0)
        ))
      )
      assert(base.size == 3)
      assert(edits.size == 3)
      val ids = edits.iterator.map { case m:Module => m.id }.toIndexedSeq
      assert(ids == Seq(1, 3, 2))
    }
  }
}