/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.spark

import play.api.libs.json._
import org.apache.spark.sql.types._
import info.vizierdb.spark.udt.ImageUDT
import org.apache.spark.unsafe.types.CalendarInterval
import org.apache.spark.sql.catalyst.expressions.{ Literal, Cast }
import java.util.{ Base64, Calendar }
import java.sql.{ Date, Timestamp }
import scala.util.matching.Regex
import scala.collection.mutable.ArraySeq
import org.apache.spark.sql.types.UDTRegistration
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.sedona_sql.UDT.RasterUDT
import org.locationtech.jts.geom.Geometry
import org.apache.spark.sql.catalyst.util.GenericArrayData
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.Row
import org.apache.sedona.core.formatMapper.FormatMapper
import org.apache.sedona.common.enums.FileDataSplitter
import org.apache.sedona.common.raster.RasterOutputs
import org.apache.sedona.common.raster.RasterConstructors
import org.apache.sedona.common.raster.{ Serde => SedonaRasterSerde }
import org.geotools.coverage.grid.GridCoverage2D
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import org.apache.spark.sql.catalyst.util.ArrayData
import org.apache.sedona.sql.utils.GeometrySerializer
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.types.{ UserDefinedType, BinaryType }
import info.vizierdb.VizierException
import org.apache.spark.ml.linalg
import info.vizierdb.serialized.MLVector
import info.vizierdb.serializers.mlvectorFormat
import info.vizierdb.util.JsonUtils
import org.apache.spark.sql.catalyst.expressions.UnsafeArrayData

object SparkPrimitive
  extends Object
  with LazyLogging
{
  def base64Encode(b: Array[Byte]): String =
    Base64.getEncoder().encodeToString(b)

  def base64Decode(b: String): Array[Byte] =
    Base64.getDecoder().decode(b)

  def formatDate(date: Date): String = {
    val cal = Calendar.getInstance();
    cal.setTime(date)
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH)+1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    f"$y%04d-$m%02d-$d%02d"
  }

  def formatTimestamp(timestamp: Timestamp): String = {
    val cal = Calendar.getInstance()
    cal.setTime(timestamp)
    val y   = cal.get(Calendar.YEAR)
    val m   = cal.get(Calendar.MONTH)+1 // Calendar.MONTH is 0-based
    val d   = cal.get(Calendar.DAY_OF_MONTH)
    val hr  = cal.get(Calendar.HOUR_OF_DAY)
    val min = cal.get(Calendar.MINUTE)
    val sec = cal.get(Calendar.SECOND)
    val ms  = cal.get(Calendar.MILLISECOND)
    f"$y%04d-$m%02d-$d%02d $hr%02d:$min%02d:$sec%02d.$ms%03d"
  }

  val DateString = "([0-9]{4})-([0-9]{2})-([0-9]{2})".r
  val TimestampString = "([0-9]{4})-([0-9]{2})-([0-9]{2})[ T]([0-9]{2}):([0-9]{2}):([0-9.]+)".r

  def decodeDate(date: String): Date = 
    date match {
      case DateString(y, m, d) => {
        val cal = Calendar.getInstance()
        cal.set(
          y.toInt, 
          m.toInt-1, // Expects a value with Jan as 1
          d.toInt, 
          0, 0, 0
        )
        new Date(cal.getTimeInMillis)
      }
      case _ => throw new IllegalArgumentException(s"Invalid Date: '$date'")
    }

  def decodeTimestamp(timestamp: String): Timestamp = 
    timestamp match {
      case TimestampString(y, m, d, hr, min, sec) => {
        val cal = Calendar.getInstance()
        val secWithMsec = sec.toDouble
        cal.set(
          y.toInt, 
          m.toInt-1, // Expects a value with Jan as 0
          d.toInt, 
          hr.toInt,
          min.toInt,
          secWithMsec.toInt
        )
        // Technically Timestamp gets more than millisecond precision, but Spark's internal
        // timestamps don't support nanosecond precision.
        val extraMilliseconds = ((secWithMsec - secWithMsec.toInt.toDouble) * 1000).toInt
        new Timestamp(cal.getTimeInMillis + extraMilliseconds)
      }
      case _ => throw new IllegalArgumentException(s"Invalid Timestamp: '$timestamp'")
    }

  lazy val geometryFormatMapper = 
    new FormatMapper(FileDataSplitter.WKT, false)

  def encodeStruct(k: Any, t: StructType): JsObject =
  {
    JsObject(
      k match {
        case row: InternalRow => 
          t.fields.zipWithIndex.map { case (field, idx) => 
            field.name -> encode(row.get(idx, field.dataType), field.dataType)
          }.toMap
        case row: Row => 
          t.fields.zipWithIndex.map { case (field, idx) => 
            field.name -> encode(row.get(idx), field.dataType)
          }.toMap
        case _ => throw new IllegalArgumentException(s"Invalid struct value of class ${k.getClass.getName} for struct $t")
      }
    )
  }

  /**
   * Encode the provided spark value into Json
   * @param   k           The spark value to encode
   * @param   t           The data type of the spark value
   */
  def encode(k: Any, t: DataType): JsValue =
  {
    logger.trace(s"ENCODE $t: \n$k")
    (t, k) match {
      case (_, null)                   => JsNull
      case (StringType, _)             => JsString(k.toString)
      case (BinaryType, _)             => JsString(base64Encode(k.asInstanceOf[Array[Byte]]))
      case (ut:UserDefinedType[_], _)     => 
      {
        // GeometryUDT is broken: https://issues.apache.org/jira/browse/SEDONA-89?filter=-2
        // so we need to do a manual comparison here.

        if(t.isInstanceOf[GeometryUDT]){
          k match {
            case geom:Geometry => JsString(geom.toText)
            case enc:ArrayData => JsString(GeometrySerializer.deserialize(enc.toByteArray()).toText)
          }
        } else if(t.isInstanceOf[RasterUDT]){
          JsString(base64Encode(SedonaRasterSerde.serialize(k.asInstanceOf[GridCoverage2D])))
        } else if(t.isInstanceOf[ImageUDT]){
          JsString(base64Encode(ImageUDT.serialize(k.asInstanceOf[BufferedImage]).asInstanceOf[Array[Byte]]))
        } else if(t.getClass().getName() == "org.apache.spark.ml.linalg.VectorUDT"
                  || t.getClass().getName() == "org.apache.spark.mllib.linalg.VectorUDT") {
          Json.toJson(
            k match {
              case linalg.SparseVector(size, indices, values) =>
                MLVector(true, size, indices, values)
              case linalg.DenseVector(values) =>
                MLVector(false, 0, Seq(), values)
            }
          )
        } else if(!ut.sqlType.isInstanceOf[UserDefinedType[_]]){
          encode(ut.asInstanceOf[UserDefinedType[Any]].serialize(k), ut.sqlType)
        } else {
          throw new VizierException(s"Unsupported UDT: $t (${t.getClass().getName()}")
        }
      }

      case (BooleanType, _)            => JsBoolean(k.asInstanceOf[Boolean])
      case (DateType, _)               => JsString(formatDate(k.asInstanceOf[Date]))
      case (TimestampType, _)          => JsString(formatTimestamp(k.asInstanceOf[Timestamp]))
      case (CalendarIntervalType, _)   => Json.obj(
                                            "months"       -> k.asInstanceOf[CalendarInterval].months,
                                            "days"         -> k.asInstanceOf[CalendarInterval].days,
                                            "microseconds" -> k.asInstanceOf[CalendarInterval].microseconds
                                          )
      case (DoubleType,_) if k.asInstanceOf[Double].isInfinite
                                       => JsString( (if(k.asInstanceOf[Double] < 0){ "-" } else { "" }) + "infinity" )
      case (DoubleType,_)              => JsNumber(k.asInstanceOf[Double])
      case (FloatType,_) if k.asInstanceOf[Float].isInfinite
                                       => JsString( (if(k.asInstanceOf[Float] < 0){ "-" } else { "" }) + "infinity" )
      case (FloatType,_)               => JsNumber(k.asInstanceOf[Float])
      case (ByteType,_)                => JsNumber(k.asInstanceOf[Byte])
      case (IntegerType,_)             => JsNumber(k.asInstanceOf[Integer]:Int)
      case (LongType,_)                => JsNumber(k.asInstanceOf[Long])
      case (ShortType,_)               => JsNumber(k.asInstanceOf[Short])
      case (NullType,_)                => JsNull
      case (ArrayType(element,_),_)    => 
        JsArray((k match {
          case i:Iterable[_] => i
          case u:UnsafeArrayData => {
            element match {
              case BooleanType => u.toBooleanArray()
              case ByteType => u.toByteArray()
              case ShortType => u.toShortArray()
              case IntegerType => u.toIntArray()
              case LongType => u.toLongArray()
              case FloatType => u.toFloatArray()
              case DoubleType => u.toDoubleArray()
            }
          }: Iterable[Any]
          case a:ArrayData => a.array: Iterable[Any]
        }).map { encode(_, element) }.toSeq)
      case (s:StructType,_)            => encodeStruct(k, s)
                                       // Encode Geometry as WKT

      case (DecimalType(),d:BigDecimal)=> JsNumber(d)
      case (DecimalType(),d:Decimal)   => JsNumber(d.toBigDecimal)
      case (DecimalType(),d:java.math.BigDecimal)
                                       => JsNumber(d)
      case (MapType(keyType, valueType, _), elems: Map[_, _]) => JsArray(
                                            elems.map { case (k, v) => 
                                              Json.obj(
                                                "key" -> encode(k, keyType),
                                                "value" -> encode(v, valueType)
                                              )
                                            }.toIndexedSeq
                                          )
      case _ if k != null           => JsString(k.toString)
      case _                        => JsNull
    }
  }

  def decodeStruct(k: JsValue, t: StructType, castStrings: Boolean = false): Any =
    InternalRow.fromSeq(
      t.fields.map { field => 
        (k \ field.name).asOpt[JsValue]
                        .map { decode(_, field.dataType, castStrings = castStrings) }
                        .getOrElse { null }
      }
    )

  def decode(k: JsValue, t: DataType, castStrings: Boolean = false): Any = 
  {

    // The following matching order is important
    logger.trace(s"DECODE $t: \n$k")

    try {
      (k, t) match {  
        // Check for null first to avoid null pointer errors
        case (JsNull, _)                    => null

        // Check for string-based parsing before the cast-strings fallback
        case (JsString(str), StringType)    => str
        case (JsNumber(num), StringType)    => num.toString()
        case (JsBoolean(b), StringType)     => b.toString()

        // Formats that are encoded as strings, but use a non-string internal
        // representation need to come next, before the cast-strings fallback
        case (_, DateType)                  => decodeDate(k.as[String])
        case (_, TimestampType)             => decodeTimestamp(k.as[String])
        case (_, BinaryType)                => base64Decode(k.as[String])
        case (_, ut:UserDefinedType[_])        => 
        {
          // GeometryUDT is broken: https://issues.apache.org/jira/browse/SEDONA-89?filter=-2
          // so we need to do a manual comparison here.

          if(t.isInstanceOf[GeometryUDT]){
            geometryFormatMapper.readGeometry(k.as[String]) // parse as WKT
          } else if(t.isInstanceOf[RasterUDT]){
            SedonaRasterSerde.deserialize(base64Decode(k.as[String]))
          } else if(t.isInstanceOf[ImageUDT]){
            ImageUDT.deserialize(base64Decode(k.as[String]))
          } else if(t.getClass().getName() == "org.apache.spark.ml.linalg.VectorUDT"
                    || t.getClass().getName() == "org.apache.spark.mllib.linalg.VectorUDT") {
            val v = k.as[MLVector]
            if(v.sparse){
              new linalg.SparseVector(v.size, v.indices.toArray, v.values.toArray)
            } else {
              new linalg.DenseVector(v.values.toArray)
            }
          } else if(!ut.sqlType.isInstanceOf[UserDefinedType[_]]) {
            ut.deserialize(decode(k, ut.sqlType))
          } else {
            throw new VizierException(s"Unsupported UDT: $t (${t.getClass().getName}")
          }
        }

        // Now that we've gotten through all String types, check if we still have
        // a string and fall back to string parsing if so.
        case (_:JsString, _) if castStrings => Cast(Literal(k.as[String]), t).eval()

        // Finally, types with native Json parsers.  These can come in any order
        case (_, BooleanType)               => k.as[Boolean]
        case (_, CalendarIntervalType)      => {
          val fields = k.as[Map[String,JsValue]]
          new CalendarInterval(fields("months").as[Int], fields("days").as[Int], fields("microseconds").as[Int])
        }
        case (JsString("infinity"), DoubleType)  => Double.PositiveInfinity
        case (JsString("-infinity"), DoubleType) => Double.NegativeInfinity
        case (_, DoubleType)                => k.as[Double]
        case (JsString("infinity"), FloatType)   => Float.PositiveInfinity
        case (JsString("-infinity"), FloatType)  => Float.NegativeInfinity
        case (_, FloatType)                 => k.as[Float]
        case (_, ByteType)                  => k.as[Byte]
        case (_, IntegerType)               => k.as[Int]:Integer
        case (_, LongType)                  => k.as[Long]
        case (_, ShortType)                 => k.as[Short]
        case (_, DecimalType())             => k.as[java.math.BigDecimal]
        case (_, NullType)                  => JsNull
        case (_, ArrayType(element,_))      => ArraySeq(k.as[Seq[JsValue]].map { decode(_, element) }:_*)
        case (_, s:StructType)              => decodeStruct(k, s, castStrings = castStrings)
        case (JsObject(elems), MapType(keyType, valueType, _)) => 
                        elems.map { case (key, value) => 
                                        ( decode(JsString(key), keyType, castStrings), 
                                          decode(value, valueType, castStrings)
                                        ) }.toMap
        case (JsArray(elems), MapType(keyType, valueType, _)) => 
                        elems.map { arr =>
                                    val fields = arr.as[Map[String, JsValue]]
                                    ( decode(fields("key"), keyType, castStrings), 
                                      decode(fields("value"), valueType, castStrings)
                                    ) }.toMap
        case (_, _:MapType)                 => throw new JsResultException(Seq())
        case _                    => throw new IllegalArgumentException(s"Unsupported type for decode: $t; ${t.getClass()}")
      }
    } catch {
      case e: JsResultException =>
        throw new VizierException(s"Failed to decode $k as a $t (cast strings = $castStrings): ${JsonUtils.prettyJsonParseError(e).mkString("; ")}")    }
  }

  implicit val dataTypeFormat = SparkSchema.dataTypeFormat
  // implicit def dataTypeFormat: Format[DataType] = Format(
  //   new Reads[DataType] { def reads(j: JsValue) = JsSuccess(DataType.fromJson(j.toString)) },
  //   new Writes[DataType] { def writes(t: DataType) = JsString(t.typeName) }
  // )
}
