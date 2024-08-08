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
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types._
import org.apache.spark.sql.types.UDTRegistration
import info.vizierdb.spark.udt.ImageUDT
import org.apache.spark.mllib.linalg.VectorUDT
import info.vizierdb.util.StringUtils
import info.vizierdb.Vizier
import scala.collection.mutable
import info.vizierdb.Plugin.PluginUDTByName
import info.vizierdb.Plugin.PluginUDTByType

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
          case MapType(keyType, valueType, containsNulls) =>
            JsString("map:" + Json.obj(
              "key" -> writes(keyType),
              "value" -> writes(valueType),
              "nulls" -> containsNulls
            ).toString)
          case _ => JsString(encodeType(d))
        }
    }
  )

  lazy val vectorSingleton = new VectorUDT

  def loadUserDefinedType(fullyQualifiedName: String): UserDefinedType[_] =
  {
    val clazz = Class.forName(fullyQualifiedName, true, Vizier.mainClassLoader)
    clazz.newInstance().asInstanceOf[UserDefinedType[_]]
  }

  def decodeType(t: String): DataType =
  {
    t match  {
      case "varchar" => StringType
      case "int" => IntegerType
      case "real" => DoubleType
      case "vector" => vectorSingleton
      case "binary" => BinaryType
      case "image/png" => ImageUDT
      case _ if t.startsWith("[") || t.startsWith("{") => 
        Json.parse(t).as[DataType]
      case _ if t.startsWith("array:") => 
        ArrayType(decodeType(t.substring(6)))
      case _ if t.startsWith("udt:") =>
        loadUserDefinedType(t.substring(4))
      case _ if t.startsWith("map:") => 
        {
          val map = Json.parse(t.substring(4))
          MapType(
            (map \ "key").as[DataType],
            (map \ "value").as[DataType],
            (map \ "nulls").as[Boolean]
          )
        }
      case PluginUDTByName(p) => p.dataType
      case _ => 
        DataType.fromJson("\""+t+"\"")
    }
  }

  def encodeType(t: DataType): String =
  {
    t match {
      case (_:ArrayType) | (_:StructType) | (_:MapType) => Json.toJson(t).toString
      case DoubleType => "real"
      case IntegerType => "int"
      case BinaryType => "binary"
        // Something changed in a recent version of spark/scala and now
        // any subclass of UserDefinedType seems to match any other
        // subclass of the same.  Need to use an explicit isInstanceOf
      case _ if t.isInstanceOf[VectorUDT] => "vector"
      case _ if t.isInstanceOf[ImageUDT] => "image/png"
      case PluginUDTByType(p) => p.shortName
      case udt:UserDefinedType[_] => 
        {
          // TODO: We need cleaner UDT handling.  Convention in most UDT-based systems is to 
          // adopt a UDT object with the same name as the actual UDT.  Drop down to the base
          // UDT if we see this
          var udtName = t.getClass().getCanonicalName()
          if(udtName.endsWith("$")){ udtName = udtName.dropRight(1) }
          /* return */ "udt:"+udtName
        }
      case _ => t.typeName
    }
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
