package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.specs2.specification.core.Fragment

class SQLSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Run queries" >> 
  {
    val workflow = MutableProject("Run queries")
    workflow.load("test_data/r.csv", "foo")
    workflow.sql("SELECT count(*) FROM foo" -> "foo")
    workflow.waitUntilReadyAndThrowOnError
    workflow.dataframe("foo").take(1)(0).getLong(0) must beEqualTo(7l)
  }

  "Not run commands" >>
  {
    Fragment.foreach(Seq(
      "Insert" -> "INSERT INTO foo VALUES (1,1), (2,2);",
      "Create Table" -> "CREATE TABLE foo(a int, b int);",
      "Update" -> "UPDATE foo SET a = b * 2;",
      "Alter" -> "ALTER TABLE foo ADD COLUMN A int;",
    )) { case (label, query) =>
      label >> {
        val workflow = MutableProject("Not run commands")
        workflow.sql(query -> null, waitForResult = false) 
        workflow.waitUntilReadyAndThrowOnError must throwA[RuntimeException]
        workflow.lastOutputString must startWith("Only queries are supported in SQL")
      }
    }
  }
}