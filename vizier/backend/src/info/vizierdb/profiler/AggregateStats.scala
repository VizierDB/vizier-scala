package info.vizierdb.profiler

import play.api.libs.json.{ Json, JsValue, Writes }
import org.apache.spark.sql.{ DataFrame, Column, Row }
import org.apache.spark.sql.types.{ DataType, StructField, NumericType, FloatType }
import org.apache.spark.sql.functions._
import DataProfiler.{ DatasetProperty, ColumnProperty, ColumnName } 

object AggregateStats extends ProfilerModule
{

  def ExtractAs[T](implicit encode: Writes[T]): (Row, Int) => JsValue = 
    { (row, i) => Json.toJson(
        if(row.isNullAt(i)){ None }
        else { Option(row.getAs[T](i)) }
      ) }

  def AnyTypeStat[T](f: Column => Column)
                         (implicit encode: Writes[T]):
                  (Column, DataType) => Option[(Column, (Row, Int) => JsValue)] = 
    { (col, t) => Some( (f(col), ExtractAs[T]) ) }

  def NumericStat[T](f: Column => Column)
                (implicit encode: Writes[T]):
                  (Column, DataType) => Option[(Column, (Row, Int) => JsValue)] =
    {
      case (col, t:DataType) if t.isInstanceOf[NumericType] => 
        Some( (f(col), ExtractAs[T]) )
      case _ => None
    }

  val tableAggregates = 
    Map[String, (Column, (Row, Int) => JsValue)](
      "count" -> (count("*"), ExtractAs[Long])
    )

  val columnAggregates = 
    Map[String, (Column, DataType) => Option[(Column, (Row, Int) => JsValue)]](
      "count"                -> AnyTypeStat[Long] { count(_) },
      "distinctValueCount"   -> AnyTypeStat[Long] { approx_count_distinct(_) },
      "nullCount"            -> AnyTypeStat[Long] { in => sum( when(in.isNull, 1).otherwise(0) ) },
      "sum"                  -> NumericStat[Float] { in => nanvl(sum(in).cast(FloatType), lit(null)) },
      "mean"                 -> NumericStat[Float] { in => nanvl(avg(in).cast(FloatType), lit(null)) },
      "stdDev"               -> NumericStat[Float] { in => nanvl(stddev(in).cast(FloatType), lit(null)) },
      "min"                  -> NumericStat[Float] { in => nanvl(min(in).cast(FloatType), lit(null)) },
      "max"                  -> NumericStat[Float] { in => nanvl(max(in).cast(FloatType), lit(null)) },
    )

  val depends = (Set(), Set("column"))
  val provides = (
    tableAggregates.keySet,
    columnAggregates.keySet
  )

  def apply(df: DataFrame, inputs: (Map[DatasetProperty, JsValue], Map[ColumnName, Map[ColumnProperty, JsValue]])): 
    (Map[DatasetProperty,JsValue], Map[ColumnName,Map[ColumnProperty,JsValue]]) = 
  {
    val schema = df.schema.fields

    // Fields: 
    //  - Target Column (None for Dataset)
    //  - Aggregate Expression
    //  - Property Name
    //  - Data Extractor
    //  - Position
    val queries: Seq[(Option[String], Column, String, Row => JsValue, Int)] = 
      (
        tableAggregates.map { 
          case (property, (aggregate, extractor)) => 
            (None, aggregate, property, extractor)
        } ++ schema.flatMap { case StructField(column, t, _, _) =>
          columnAggregates.flatMap {
            case (property, constructor) =>
              constructor(df(column), t).map { 
                case (aggregate, extractor) => 
                  (Some(column), aggregate, property, extractor)
              }
          }
        }
      ).zipWithIndex.map { 
          case ((column, aggregate, property, extractor), index) =>
            (column, aggregate, property, extractor(_, index), index)
      }.toSeq

    val statsRow = 
      df.select(queries.map { q => q._2.as(s"stat_${q._5}") }:_*)
        .take(1)
        .head

    val (datasetQueries, columnQueries) = 
      queries.partition { _._1.isEmpty }


    return (
      datasetQueries.map { q => q._3 -> q._4(statsRow) }.toMap,
      columnQueries.map { q => (q._1.get, q._3, q._4(statsRow)) }
                   .groupBy { _._1 }
                   .mapValues { _.map { property => property._2 -> property._3 }.toMap }
    )
  }
}