package info.vizierdb.profiler

import java.util.{ Map => JavaMap }

import scala.collection.mutable.WrappedArray
import play.api.libs.json.{ Json, JsValue, JsString, JsNumber } 
import org.apache.spark.sql.{ DataFrame, Column, Row }
import DataProfiler.{ DatasetProperty, ColumnProperty, ColumnName } 
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.expressions.Aggregator
import org.apache.spark.sql.{ Encoders, Encoder }
import org.apache.spark.sql.types.{ NumericType, StringType, FloatType }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder



case class BuildHistogram(min: Double, buckets: Array[Double])
  extends Aggregator[Option[Double], Array[Long], Array[(String, Long)]]
{
  def zero = (0 until (buckets.size+1)).map { _ => 0l }.toArray
  
  def reduce(buffer: Array[Long], elemMaybe: Option[Double]): Array[Long] = 
  {
    elemMaybe match {
      case None => buffer
      case Some(elem) => {
        val idx = buckets.foldLeft(0) { (count, boundary) => 
                    if(elem > boundary) { count + 1 } else { count } 
                  }
        buffer.patch(idx, Array(buffer(idx) + 1l), 1)
      }
    }
  }
  
  def merge(a: Array[Long], b:Array[Long]): Array[Long] =
  {
    a.zip(b).map { case (countA, countB) => countA + countB }
  }

  def finish(buffer: Array[Long]): Array[(String, Long)] =
  {
    (min.toString +: buckets.map{ _.toString }).zip(buffer)
  }

  def bufferEncoder: Encoder[Array[Long]] = ExpressionEncoder[Array[Long]]
  def outputEncoder: Encoder[Array[(String, Long)]] = ExpressionEncoder[Array[(String, Long)]]
}

object BuildCategories
  extends Aggregator[String, Map[String, Long], Array[(String, Long)]]
{
  def zero = Map[String, Long]()
  
  def reduce(buffer: Map[String, Long], elem: String): Map[String,Long] = 
  {
    val key = if(elem == null){ "" } else { elem.toString }
    buffer ++ Map(key -> (buffer.getOrElse(key, 0l) + 1l))
  }
  
  def merge(a: Map[String,Long], b:Map[String,Long]): Map[String,Long] =
  {
    (a.keySet ++ b.keySet).map { k =>
      k -> (a.getOrElse(k, 0l) + b.getOrElse(k, 0l))
    }.toMap
  }

  def finish(buffer: Map[String,Long]): Array[(String, Long)] =
  {
    buffer.toArray
  }

  def bufferEncoder: Encoder[Map[String,Long]] = ExpressionEncoder[Map[String,Long]]
  def outputEncoder: Encoder[Array[(String, Long)]] = ExpressionEncoder[Array[(String, Long)]]
}



/**
 *  "values": [
      {
        "name": "1",
        "count": 2
      },
      {
        "name": "2",
        "count": 2
      },
      {
        "name": "3",
        "count": 2
      },
      {
        "name": "5",
        "count": 2
      }
    ]
  */
object HistogramStats extends ProfilerModule
{
  val NUM_BUCKETS = 10
  val DISTINCT_VALUE_THRESHOLD = 20
  val COUNT_THRESHOLD = 5000

  val depends = (Set(), Set("column", "distinctValueCount", "min", "max"))
  val provides = (Set(), Set("values"))

  def apply(
    df: DataFrame, 
    inputs: (Map[DatasetProperty,JsValue], Map[ColumnName,Map[ColumnProperty,JsValue]])
  ): (Map[DatasetProperty,JsValue], Map[ColumnName,Map[ColumnProperty,JsValue]]) = 
  {
    val query: Seq[(String, Column, Int)] =
      df.schema
        .fields
        .flatMap { case StructField(name, t, _, _) => 
          val props = inputs._2.getOrElse(name, Map())

          if(t.isInstanceOf[NumericType] && props.contains("min") && props.contains("max")){
            val min = props("min").as[Float]
            val max = props("max").as[Float]
            val buckets = (1 until NUM_BUCKETS).map { idx => min + (max - min) * (idx.toDouble / NUM_BUCKETS) }
            Some(name -> udaf(BuildHistogram(min, buckets.toArray)).apply(df(name).cast(FloatType)))
          } else if(props.get("distinctValueCount")
                         .map { _.as[Int] < DISTINCT_VALUE_THRESHOLD }
                         .getOrElse(false)) {
            Some(name -> udaf(BuildCategories).apply(df(name).cast(StringType)))
          } else {
            None
          }
        }
        .zipWithIndex
        .map { case ((name, fn), idx) => (name, fn, idx) }

    val count = inputs._1("count").as[Long]

    val (sample, scale):(DataFrame, Float) = 
      if(count > COUNT_THRESHOLD){ 
        val sampleRate = COUNT_THRESHOLD.toFloat / count
        (df.sample(sampleRate), (1 / sampleRate).toFloat)
      } else { (df, 1.0.toFloat) }

    val resultRow:Row = 
      df.select(query.map { _._2 }:_*)
        .take(1)
        .head

    return (
      Map(),
      query.map { case (name, _, idx) => 
        name -> Map(
          "values" -> 
            Json.toJson(
              resultRow.getAs[WrappedArray[Row]](idx)
                       .map { row => 
                          Map(
                            "name" -> JsString(row.getString(0)),
                            "count" -> JsNumber((row.getLong(1).toFloat * scale).toLong)
                          )
                       }.toSeq
            )
        )
      }.toMap
    )
  }
}