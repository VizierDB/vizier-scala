package info.vizierdb.commands

import scalikejdbc.DB
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.test.SharedTestResources
import info.vizierdb.viztrails.MutableProject
import org.mimirdb.vizual._
import org.apache.spark.sql.types._

class VizualCommandSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val project = MutableProject("Vizual Commands Test")

  "UpdateCell Like/Join Regression" >> 
  {

    project.load("test_data/explosions.csv", "R")
    // project.show("R")
    
    val rowids = project.artifact("R").getDataset().prov
    
    project.sql("""
      SELECT A, explode(split(B, '/')) AS grade FROM R
    """ -> "S")
    // project.show("S")

    project.vizual("R", 
      (
        InsertColumn(Some(2), "C")
        +: rowids.zipWithIndex.map { case (rowid, idx) => 
          UpdateCell(2, Some(RowsById(Set(rowid))), Some(s"Row ${idx+1}"))
        }
      ):_*
    )
    // project.show("R")

    project.sql("""
      SELECT S.A, S.grade, R.C
      FROM S, R
      WHERE S.A = R.A
       AND S.A NOT LIKE 'g%'
      ORDER BY S.grade
    """ -> "S")
    // project.show("S")

    project.artifact("S")
           .getDataset()
           .data
           .map { _(2) must not beNull }
    
  }
}