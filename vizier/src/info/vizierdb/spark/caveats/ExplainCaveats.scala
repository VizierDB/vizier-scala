package info.vizierdb.spark.caveats

import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.mimirdb.caveats.{ Caveat, CaveatSet }
import org.mimirdb.caveats.implicits._
import info.vizierdb.Vizier
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

object ExplainCaveats
  extends LazyLogging
{
  implicit val ec = scala.concurrent.ExecutionContext.global

  def apply(
    table: DataFrame, 
    rows: Seq[String] = null,
    cols: Seq[String] = null,
    schemaCaveats: Boolean = true,
    reasonCap: Int = 3,
    spark: SparkSession = Vizier.sparkSession
  ): Seq[Caveat] =
  {
    val caveatSets = coarsely(table, rows, cols, schemaCaveats, spark)
    caveatSets.map { caveatSet =>
                Future { 
                  val caveats = caveatSet.take(spark, reasonCap+1)
                  logger.trace(s"Expanding CaveatSet: \n${caveatSet}")
                  if(caveats.size > reasonCap){
                    caveats.slice(0, reasonCap) :+
                      Caveat(
                        s"... and ${caveatSet.size(spark) - reasonCap} more like the last",
                        None,
                        Seq()
                      )
                  } else {
                    caveats
                  }
                }
              }
              .flatMap { Await.result(_, Duration.Inf) }

  }

  def coarsely(
    table: DataFrame, 
    rows: Seq[String] = null,
    cols: Seq[String] = null,
    schemaCaveats: Boolean = true,
    spark: SparkSession = Vizier.sparkSession
  ): Seq[CaveatSet] = 
  {
    var df = table
    val selectedCols = 
      Option(cols).getOrElse { df.schema.fieldNames.toSeq }.toSet
    if(rows != null){
      df = AnnotateWithRowIds(df)
      // println(s"EXPLAIN PLAN: \n${df.queryExecution.logical}")
      df = df.filter { df(AnnotateWithRowIds.ATTRIBUTE).isin(rows:_*) }
    }
    df.listCaveatSets(row = true, attributes = selectedCols)
  }
}