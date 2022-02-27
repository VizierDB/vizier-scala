package info.vizierdb.spark

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types._
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.types.UDTRegistration
import info.vizierdb.spark.udt.ImageUDT
import org.apache.spark.mllib.linalg.VectorUDT
import info.vizierdb.util.StringUtils

object SparkSchema {
  def apply(df: DataFrame): Seq[StructField] =
    df.schema.fields

  def apply(name: String, t: String): StructField = 
    StructField(name, decodeType(t))

  implicit val dataTypeFormat = Format[DataType](
    new Reads[DataType] {
      def reads(j: JsValue): JsResult[DataType] =
        j match {
          case JsObject(elems) => 
            JsSuccess(StructType(
              elems.toArray.map { case (name, element) => 
                StructField(name, reads(element).get)
              }
            ))
          case JsArray(elems) =>
            JsSuccess(ArrayType(reads(elems(0)).get))
          case JsString(elem) => 
            JsSuccess(decodeType(elem))
          case _ => 
            JsError("Not a valid datatype")
        }
    },
    new Writes[DataType] {
      def writes(d: DataType): JsValue =
        d match {
          case ArrayType(element, _) => 
            JsArray(Seq(writes(element)))
          case StructType(fields) => 
            JsObject(
              fields.map { field => 
                field.name -> writes(field.dataType)
              }.toMap
            )
          case _ => JsString(encodeType(d))
        }
    }
  )

  lazy val vectorSingleton = new VectorUDT

  def decodeType(t: String): DataType =
    t match  {
      case "varchar" => StringType
      case "int" => IntegerType
      case "real" => DoubleType
      case "geometry" => GeometryUDT
      case "vector" => vectorSingleton
      case "binary" => BinaryType
      case "image/png" => ImageUDT
      case _ if t.startsWith("[") || t.startsWith("{") => 
        Json.parse(t).as[DataType]
      case _ if t.startsWith("array:") => 
        ArrayType(decodeType(t.substring(6)))
      case _ => 
        DataType.fromJson("\""+t+"\"")
    }

  def encodeType(t: DataType): String =
    t match {
      case (_:ArrayType) | (_:StructType) => Json.toJson(t).toString
      case DoubleType => "real"
      case IntegerType => "int"
      case BinaryType => "binary"
        // Something changed in a recent version of spark/scala and now
        // any subclass of UserDefinedType seems to match any other
        // subclass of the same.  Need to use an explicit isInstanceOf
      case _ if t.isInstanceOf[GeometryUDT] => "geometry"
      case _ if t.isInstanceOf[VectorUDT] => "vector"
      case _ if t.isInstanceOf[ImageUDT] => "image/png"
      case _ => t.typeName
    }


  implicit val fieldFormat = Format[StructField](
    new Reads[StructField] { 
      def reads(j: JsValue): JsResult[StructField] = 
      {
        val fields = j.as[Map[String, JsValue]]
        return JsSuccess(StructField(
          fields
            .get("name")
            .getOrElse { return JsError("Expected name field") }
            .as[String],
          decodeType(
            fields
              .get("type")
              .getOrElse { return JsError("Expected type field") }
              .as[String] 
          )
        ))
      }
    }, 
    new Writes[StructField] {
      def writes(s: StructField): JsValue =
      {
        val t = encodeType(s.dataType)
        Json.obj(
          "name" -> s.name,
          "type" -> t,
          "baseType" -> t
        )
      }
    }
  )

  def friendlyTypeString(dataType: DataType): String = 
  {
    dataType match {
      case StringType => "string"
      case IntegerType => "4 byte integer"
      case LongType => "8 byte integer"
      case ShortType => "8 byte integer"
      case FloatType => "single precision float"
      case DoubleType => "double precision float"
      case ArrayType(elem, _) => "array of "+StringUtils.plural(friendlyTypeString(elem))
      case _ => dataType.json
    }
  }
}
