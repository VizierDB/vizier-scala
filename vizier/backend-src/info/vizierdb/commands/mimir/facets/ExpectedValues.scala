package info.vizierdb.commands.mimir.facets

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.types._

case class ExpectedValues(column: String, values: Seq[String])
  extends Facet
  with LazyLogging
{
  def identity = ExpectedValues.identity
  def description = 
    s"$column only takes values in ${values.take(5).map { "'"+_+"'" }.mkString(", ")}${if(values.size > 5){ ", ..." } else { "" }}"

  def test(query: DataFrame): Seq[String] =
  {
    if(!(query.columns contains column)){ return Seq() }

    val offenders = 
      query.select(query(column))
           .filter{ not(query(column).isInCollection(values)) }
           .distinct
           .take(11)
           .map { _.get(0).toString }
    if(offenders.size > 0){
      Seq(
        (
          s"$column had only values in "+
          values.take(5).map { "'"+_+"'" }.mkString(", ")+
          (if(values.size > 5){ ", ..." } else { "" })+
          " before, but now has "+
          offenders.take(10).mkString(", ")+
          (if(offenders.size > 10){ ", ... ("+offenders.size+" unexpected values in total)" } else { "" })
        )
      )
    } else { Seq.empty }
  }
  def toJson = Json.toJson(this)
  def affectsColumn = Some(column)
}


object ExpectedValues
  extends FacetDetector
  with LazyLogging
{
  def identity = "ExpectedValues"
  implicit val format: Format[ExpectedValues] = Json.format

  def apply(query: DataFrame): Seq[Facet] =
  {
    val stringColumnNames = 
      query.schema
           .fields
           .collect { 
             case StructField(name, StringType, _, _) => name
           }

    val (numRows, approxCounts) = 
      query
        .agg(
          sum(lit(1l)).as("__COUNT"), 
          stringColumnNames.map { column => 
            approxCountDistinct(column).as(column)
          }:_*
        )
        .take(1)
        .map { row => 
          ( 
            row.getAs[Long](0), 
            stringColumnNames.zipWithIndex.map { case (column, idx) =>
              column -> row.getAs[Long](idx+1)
            }
          )
        }
        .head
    
    // TODO: There's a more elegant way to figure out whether we've got 
    // an enum type or a unique-ish value: Species Accumulation Curves
    // See https://dl.acm.org/doi/10.1145/3167970 for an example applied
    // to databases. 
    // Implementing it though is going to require a nontrivial windowed
    // aggregate function, so let's take something simple and usable
    // for now. -OK

    logger.debug(s"Test found: $numRows / $approxCounts")
    val targetCount = 
      if(numRows < 250){
        // for small datasets, go by %... something like 1 value / 5 records
        (numRows / 5).toLong
      } else { 
        // otherwise cap it at 20 (for now)
        50
      }
    
    val columnsWithEnumerableTypes = 
      approxCounts.collect { 
        case (column, numValues) if numValues <= targetCount => column
      }

    return columnsWithEnumerableTypes.map { column => 
      val values = 
        query.select(query(column))
             .distinct()
             .take(60)
             .map { _.get(0).toString() }
      ExpectedValues(column, values)
    }
  }
  def decode(facet: JsValue)(): Facet = facet.as[ExpectedValues]
}
