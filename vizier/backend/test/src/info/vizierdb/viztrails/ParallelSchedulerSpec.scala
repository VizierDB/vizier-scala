package info.vizierdb.viztrails

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.serialized.Timestamps
import scala.concurrent.{ Future, Await }
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import org.specs2.matcher.Matcher
import org.specs2.matcher.Expectable

class ParallelSchedulerSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  sequential

  def time(project: MutableProject): Long = 
  {
    val startTime = System.currentTimeMillis()
    project.waitUntilReadyAndThrowOnError 
    val endTime = System.currentTimeMillis()
    return endTime - startTime    
  }

  def findPair[T](elements: Seq[T])(op: (T, T) => Boolean): Option[(T, T)] =
  {
    for(i <- 0 until elements.size){
      for(j <- i until elements.size){
        if(op(elements(i), elements(j))){ 
          return Some( (elements(i), elements(j)) )
        }
      }
    }
    return None
  }

  case class haveOverlaps() extends Matcher[Iterable[Timestamps]]
  {
    def apply[S <: Iterable[Timestamps]](v: Expectable[S]) = 
      result(
        findPair(v.value.map { ts => ts.startedAt.get -> ts.finishedAt.get }.toSeq) {
          (a, b) => (a._2 isAfter b._1) && (b._2 isAfter a._1)
        }.isDefined,
        s"Overlaps exist in \n${v.value.mkString("\n")}",
        s"No overlaps exist in \n${v.value.mkString("\n")}",
        v
      )
  }
    (ts: Seq[Timestamps]) => (
    )

  "run multiple cells in parallel" >>
  {
    val project = MutableProject("Parallel Execution Test")

    project.append("dummy", "wait")("msec" -> 1000, "message" -> "Cell 1")
    project.append("dummy", "wait")("msec" -> 1000, "message" -> "Cell 2")
    project.append("dummy", "wait")("msec" -> 1000, "message" -> "Cell 3")
    project.append("dummy", "wait")("msec" -> 1000, "message" -> "Cell 4")

    // time(project) must be_<(1500l)
    project.waitUntilReadyAndThrowOnError

    project.timestamps must haveOverlaps()

    project.lastOutputString must beEqualTo("Cell 4")

  }

  "figure out dependencies properly" >>
  {
    val project = MutableProject("Dependency Test")

    project.append("dummy", "wait")(
      "msec" -> 600, "message" -> "😼", 
      "writes" -> Seq(
        Map("dataset" -> "a")
      )
    )
    project.append("dummy", "wait")(
      "msec" -> 600, "message" -> "🧙", 
      "writes" -> Seq(
        Map("dataset" -> "b")
      )
    )
    project.append("dummy", "wait")(
      "msec" -> 200, "message" -> "👿",
      "reads" -> Seq(
        Map("dataset" -> "a"),
        Map("dataset" -> "b")
      ),
      "writes" -> Seq(
        Map("dataset" -> "c"),
      )
    )
    project.append("dummy", "wait")(
      "msec" -> 200, "message" -> "🐏",
      "reads" -> Seq(
        Map("dataset" -> "b")
      ),
      "writes" -> Seq(
        Map("dataset" -> "b")
      )
    )
    project.append("dummy", "wait")(
      "msec" -> 200, "message" -> "🐡",
      "reads" -> Seq(
        Map("dataset" -> "a"),
        Map("dataset" -> "b")
      ),
      "writes" -> Seq(
        Map("dataset" -> "d")
      )
    )
    project.append("dummy", "consume")(
      "datasets" -> Seq(
        Map("dataset" -> "c"),
        Map("dataset" -> "d")
      )
    )
    
    project.waitUntilReadyAndThrowOnError
    project(2).get.map { _.dataString }.mkString(", ") must beEqualTo("😼, 🧙, 👿")
    project(3).get.map { _.dataString }.mkString(", ") must beEqualTo("🧙, 🐏")
    project(4).get.map { _.dataString }.mkString(", ") must beEqualTo("😼, 🧙, 🐏, 🐡")
    project(5).get.map { _.dataString }.mkString(", ") must beEqualTo("😼, 🧙, 👿😼, 🧙, 🐏, 🐡")

  }

}
