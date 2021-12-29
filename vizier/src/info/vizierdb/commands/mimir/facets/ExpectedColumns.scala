package info.vizierdb.commands.mimir.facets

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import info.vizierdb.util.StringUtils

case class ExpectedColumns(expected: Seq[String])
  extends Facet
{
  def identity = ExpectedColumns.identity
  def description = s"The dataset includes columns: ${expected.mkString(", ")}"
  def test(query:DataFrame): Seq[String] =
  {
    val actualLowerCase = query.columns.map { _.toLowerCase }
    val expectedLowerCase = expected.map { _.toLowerCase }
    val actualSet = actualLowerCase.toSet
    val expectedSet = expectedLowerCase.toSet

    if(expectedSet.equals(actualSet)){
      if(expectedLowerCase.size != actualLowerCase.size){
        val actualCounts = actualLowerCase.groupBy { x => x }
        val expectedCounts = expected.groupBy { x => x.toLowerCase }.toSeq
        expectedCounts.flatMap { case (expectedLowerCase, expectedNormalCaseInstances) => 
          val actualCount = actualCounts(expectedLowerCase).size
          val expectedCount = expectedNormalCaseInstances.size
          val expectedNormalCase = expectedNormalCaseInstances.head

          if(actualCount > expectedCount){
            Some(s"Unexpected ${(actualCount - expectedCount)} extra ${StringUtils.pluralize("copy", actualCount-expectedCount)} of column $expectedNormalCase")
          } else if(actualCount < expectedCount) {
            Some(s"Unexpected ${(expectedCount - actualCount)} ${StringUtils.pluralize("copy", expectedCount-actualCount)} of column $expectedNormalCase missing")
          } else { None }
        }.toSeq
      } else {
        if(expectedLowerCase.zip(query.columns).exists { case (e, a) => !e.equalsIgnoreCase(a) }){
          Seq(s"Columns out of order: Got ${query.columns.mkString(", ")} but expected: ${expected.mkString(", ")}")
        } else {
          Seq() // all is well
        }
      }
    } else {
      (expectedSet &~ actualSet).toSeq.map { colLowerCase =>
        "Missing expected column '"+expected.find { _.equalsIgnoreCase(colLowerCase) }.get+"'"
      } ++ 
      (actualSet &~ expectedSet).toSeq.map { colLowerCase =>
        "Unexpected column '"+query.columns.find { _.equalsIgnoreCase(colLowerCase) }.get+"'"
      }
    }
  }
  def toJson = Json.toJson(this)
  def affectsColumn = None
}

object ExpectedColumns
  extends FacetDetector
{
  def identity = "ExpectedColumns"
  implicit val format: Format[ExpectedColumns] = Json.format

  def apply(query:DataFrame): Seq[Facet] = 
    Seq(new ExpectedColumns(query.columns.toSeq))
  def decode(facet: JsValue)(): Facet = facet.as[ExpectedColumns]
}