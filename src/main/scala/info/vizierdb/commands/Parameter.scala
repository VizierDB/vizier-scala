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
package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.types.ArtifactType

sealed trait Parameter
{
  def hidden: Boolean
  def id: String
  def name: String
  def required: Boolean
  def datatype: String
  def getDefault: JsValue = JsNull

  def stringify(j: JsValue): String =
    j match {
      case JsNull => "null"
      case _ => 
        if(validate(j).isEmpty){ doStringify(j) }
        else { s"invalid $datatype[$j]" }
    }

  def doStringify(j: JsValue): String
  /**
   * Check the value of the specified parameter and return an error string if it is invalid
   */
  def validate(j: JsValue): Iterable[String] =
    if(j.equals(JsNull)) {
      if(required) { Some(s"Missing parameter for $name") }
      else { None }
    } else { doValidate(j: JsValue) }
  def doValidate(j: JsValue): Iterable[String]
  def encode(v: Any): JsValue
  def describe: Map[String, JsValue] =
    Map(
      "id"       -> JsString(id),
      "name"     -> JsString(name),
      "datatype" -> JsString(datatype),
      "hidden"   -> JsBoolean(hidden),
      "required" -> JsBoolean(required)
    )
  def convertToReact(j: JsValue): JsValue = j
  def convertFromReact(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = j
}

object Parameter
{

  def describe(list: Seq[Parameter]): JsValue =
    JsArray(doDescribe(list, 0, None)._1.map { JsObject(_) })

  private def doDescribe(
    list: Seq[Parameter], 
    startIdx: Int, 
    parent: Option[String]
  ): (Seq[Map[String, JsValue]], Int) =
  {
    list.foldLeft( (Seq[Map[String, JsValue]](), startIdx) ) { 
      case ((accum, idx), curr) =>
        val currDescription =
          curr.describe ++ 
          parent.map { p => Map("parent" -> JsString(p)) }
                .getOrElse { Map.empty } ++ 
          Map("index" -> JsNumber(idx))

        curr match {
          case l:ListParameter => 
            val (components, nextIdx) = doDescribe(l.components, idx+1, Some(l.id))
            (
              accum ++ Seq(currDescription) ++ components,
              nextIdx
            )
          case l:RecordParameter => 
            val (components, nextIdx) = doDescribe(l.components, idx+1, Some(l.id))
            (
              accum ++ Seq(currDescription) ++ components,
              nextIdx
            )

          case _ => (accum ++ Seq(currDescription), idx + 1)
        }
    }
  }

}

trait StringEncoder
{
  def name: String
  def encode(v: Any): JsValue = 
    v match {
      case x:String => JsString(x)
      case None => JsNull
      case Some(x) => encode(x)
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected String, but got $v)")
    }
}

trait IntegerEncoder
{
  def name: String
  def encode(v: Any): JsValue = 
    v match {
      case x:Int => JsNumber(x)
      case x:Integer => JsNumber(x:Int)
      case x:Long => JsNumber(x)
      case None => JsNull
      case Some(x) => encode(x)
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Int/Long, but got $v)")
    }
}

trait FloatEncoder
{
  def name: String
  def encode(v: Any): JsValue = 
    v match {
      case x:Int => JsNumber(x)
      case x:Integer => JsNumber(x:Int)
      case x:Long => JsNumber(x)
      case x:Float => JsNumber(x)
      case x:Double => JsNumber(x)
      case None => JsNull
      case Some(x) => encode(x)
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Int/Long/Float/Double, but got $v)")
    }
}


case class BooleanParameter(
  id: String,
  name: String,
  default: Option[Boolean] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter
{ 
  def datatype = "bool"
  def doStringify(j: JsValue): String = j.as[Boolean].toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsBoolean]){ None }
                               else if ((j == JsNull) && (default.isDefined || !required)) { None }
                               else { Some(s"Expected a boolean for $name") }
  override def getDefault: JsValue = 
    default.map { JsBoolean(_) }.getOrElse { JsNull }
  def encode(v: Any): JsValue = 
    v match {
      case x:Boolean => JsBoolean(x)
      case _ => throw new VizierException("Invalid Parameter to $name (expected Boolean)")
    }
}

case class CodeParameter(
  id: String,
  name: String,
  language: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "code"
  def doStringify(j: JsValue): String = j.as[String].toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected a string for $name") }
  override def describe = super.describe ++ Map("language" -> JsString(language))
}

case class ColIdParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "colid"
  def doStringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else if (j.equals(JsString("")) && (!required)) { None }
                               else { Some(s"Expected a number/column id for $name") }
}

case class DatasetParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "dataset"
  def doStringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected a string/dataset id for $name") }
}

case class ArtifactParameter(
  id: String,
  name: String,
  artifactType: ArtifactType.T, 
  required: Boolean = true, 
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "artifact"
  def doStringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected a string/dataset id for $name") }
  override def describe = 
    super.describe ++ Map("artifactType" -> JsString(artifactType.toString.toLowerCase()))
}

case class DecimalParameter(
  id: String,
  name: String,
  default: Option[Double] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with FloatEncoder
{
  def datatype = "decimal"
  def doStringify(j: JsValue): String = j.as[Double].toString
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else if ((j == JsNull) && (default.isDefined || !required)) { None }
                               else { Some(s"Expected a number for $name") }
  override def getDefault: JsValue = 
    default.map { JsNumber(_) }.getOrElse { JsNull }
}

case class FileParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter
{
  def datatype = "fileid"
  def doStringify(j: JsValue): String = j.as[FileArgument].toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsObject]){ 
                                  j.as[FileArgument].validate.map { _+" for "+name }
                               } 
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected an object for $name") }
  def encode(v: Any): JsValue = 
    Json.toJson(v match {
      case s:String => FileArgument( url = Some(s) )
      case f:FileArgument => f
      case _ => throw new VizierException(s"Invalid argument to $name (Expected FileArgument or String)")
    })
}

case class IntParameter(
  id: String,
  name: String,
  default: Option[Int] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "int"
  def doStringify(j: JsValue): String = j.as[Int].toString
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else if ((j == JsNull) && (default.isDefined || !required)) { None }
                               else { Some(s"Expected a number for $name") }
  override def getDefault: JsValue = 
    default.map { JsNumber(_) }.getOrElse { JsNull }
}

case class ListParameter(
  id: String,
  name: String,
  components: Seq[Parameter],
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter 
{
  def datatype = "list"
  def doStringify(j: JsValue): String = 
  {
    val rows = j.as[Seq[Map[String, JsValue]]]
    rows.map { row => 
      "<" + components.map { t => t.stringify(row.getOrElse(t.id, t.getDefault)) }.mkString(",") + ">"
    }.mkString("; ")
  }
  def zipParameters[T](record: Map[String,T]): Seq[(Parameter, Option[T])] =
    components.map { component => component -> record.get(component.id) }

  def doValidate(j: JsValue): Iterable[String] = 
    if ((j == JsNull) && (!required)) { return None }
    else if(!j.isInstanceOf[JsArray]){ return Some(s"Expected a list for $name") }
    else { 
      j.as[Seq[JsValue]].flatMap { elem => 
        if(!elem.isInstanceOf[JsObject]) { 
          return Some("Expected list elements in $name to be objects, but $elem isn't.") 
        }
        zipParameters(elem.as[Map[String, JsValue]])
          .flatMap { case (component, v) => 
            component.validate( v.getOrElse { component.getDefault }) 
          }
      }
    }
  def encode(v: Any): JsValue = 
    v match { 
      case Seq() => JsArray()
      case elems:Seq[Any] => {
        val ret = 
          if(elems.head.isInstanceOf[Map[_,_]]){
            elems.asInstanceOf[Seq[Map[String,Any]]].map { 
              zipParameters(_).map { case (component, subV) => 
                component.id -> 
                  subV.map { component.encode(_) }.getOrElse { component.getDefault }
              }.toMap
            }
          } else if(components.length == 1) {
            elems.map { elem => Map(components.head.id -> components.head.encode(elem)) }
          } else {
            throw new VizierException(s"Invalid Parameter to $name (expected Seq to contain Maps, but instead has ${elems.head})")
          }
        return Json.toJson(ret)
      }
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Seq)")
    }
  override def convertToReact(j: JsValue): JsValue = 
  {
    Json.toJson(
      j.as[Seq[Map[String,JsValue]]].map { row =>
        components.map { param => 
          val v = row.getOrElse(param.id, param.getDefault)
          Map(
            "id" -> JsString(param.id),
            "value" -> param.convertToReact(v)
          )
        }
      }
    )
  }
  override def convertFromReact(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = 
  {
    JsArray(
      j.as[Seq[Seq[Map[String,JsValue]]]].map { case row => 
        val arguments = row.map { arg => 
                             arg("id").as[String] -> arg("value")
                           }
                          .toMap
        JsObject(
          components.flatMap { param => 
            arguments.get(param.id)
                     .map { v => 
                        param.id -> 
                          param.convertFromReact(
                            preprocess(param, v),
                            preprocess
                          )
                     }
          }
          .toMap
        )
      }
    )
  }
  override def getDefault: JsValue = JsArray(Seq())
}

case class RecordParameter(
  id: String,
  name: String,
  components: Seq[Parameter],
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "record"
  def doStringify(j: JsValue): String = 
  {
    val record = j.as[Map[String, JsValue]]
    "<" + components.map { t => t.stringify(record.getOrElse(t.id, t.getDefault)) }.mkString(", ") + ">"
  }
  def zipParameters[T](record: Map[String,T]): Seq[(Parameter, Option[T])] =
    components.map { component => component -> record.get(component.id) }

  def doValidate(j: JsValue): Iterable[String] =
    if ((j == JsNull) && (!required)) { None }
    else if(!j.isInstanceOf[JsObject]){ return Some(s"Expected an object for $name") }
    else {
      zipParameters(j.as[Map[String, JsValue]])
        .flatMap { case (component, v) => 
          component.validate( v.getOrElse { component.getDefault } )
        }
    }
  override def convertToReact(j: JsValue): JsValue = 
  {
    val record = j.as[Map[String,JsValue]]
    Json.toJson(
      components.map { param => 
        val v = record.getOrElse(param.id, param.getDefault)
        Map(
          "id" -> JsString(param.id),
          "value" -> param.convertToReact(v)
        )
      }
    )
  }
  override def convertFromReact(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = 
  {
    val arguments = j.as[Seq[Map[String,JsValue]]]
                     .map { arg => 
                         arg("id").as[String] -> arg("value")
                     }
                    .toMap
    JsObject(
      components.flatMap { param => 
        arguments.get(param.id)
                 .map { v => 
                    param.id -> 
                      param.convertFromReact(
                        preprocess(param, v),
                        preprocess
                      )
                 }
      }
      .toMap
    )
  }
}

case class RowIdParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "rowid"
  def doStringify(j: JsValue): String = j match {
    case JsNumber(n) => s"[$n]"
    case JsString(s) => s"[$s]"
    case _ => s"[???]"
  }
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber] || 
                                  j.isInstanceOf[JsString]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected a number/rowid for $name") }
}

case class ScalarParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "scalar"
  def doStringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else if ((j == JsNull) && (!required)) { None }
                               else { Some(s"Expected a number for $name") }
}

/**
 * One option for the EnumerableParameter
 * 
 * e.g., Create a list of these with
 * ```
 * EnumerableValue.withNames(
 *   key1 -> "Description 1",
 *   key2 -> "Description 2",
 *   ...
 * )
 * ```
 */
case class EnumerableValue(text: String, value: String)

/**
 * One option for the EnumerableParameter
 * 
 * e.g., Create a list of these with
 * ```
 * EnumerableValue.withNames(
 *   key1 -> "Description 1",
 *   key2 -> "Description 2",
 *   ...
 * )
 * ```
 */
object EnumerableValue
{
  def withNames(textAndValue:(String, String)*): Seq[EnumerableValue] = 
    textAndValue.map { tv => EnumerableValue(tv._1, tv._2) }
}

case class EnumerableParameter(
  id: String,
  name: String,
  values: Seq[EnumerableValue],
  default: Option[Int] = None,
  required: Boolean = true,
  hidden: Boolean = false,
  aliases: Map[String,String] = Map.empty
) extends Parameter with StringEncoder
{
  lazy val possibilities = Set(values.map { _.value }:_*) ++ aliases.keySet
  def datatype = "string"
  def doStringify(j: JsValue): String = j.as[String]
  def doValidate(j: JsValue) = 
    if(j.isInstanceOf[JsString]){ 
      if(possibilities(j.as[String])){ None }
      else {
        Some(s"Expected $name to be one of ${possibilities.mkString(", ")}, but got $j")
      }
    }
    else if ((j == JsNull) && (default.isDefined || !required)) { None }
    else { Some(s"Expected a string/enumerable for $name") }
  override def getDefault: JsValue = 
    Json.toJson(default.map { values(_).value })
  override def describe = super.describe ++ Map("values" -> JsArray(
    values.zipWithIndex.map { case (v, idx) => Json.obj(
      "isDefault" -> JsBoolean(default.map { _ == idx }.getOrElse(false)),
      "text"      -> v.text,
      "value"     -> v.value
    )}
  ))
  override def convertFromReact(j: JsValue, preprocess: (Parameter, JsValue) => JsValue): JsValue = 
  {
    super.convertFromReact(j, preprocess) match { 
      case JsString(key) => JsString(aliases.getOrElse(key, key))
      case x => x
    }
  }
}

case class StringParameter(
  id: String,
  name: String,
  default: Option[String] = None,
  required: Boolean = true,
  hidden: Boolean = false,
  relaxed: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "string"
  def doStringify(j: JsValue): String = 
    j match {
      case JsString(s) => s
      case JsBoolean(b) if relaxed => b.toString()
      case JsNumber(n) if relaxed => n.toString()
      case JsNull if required => default.getOrElse("<undefined>")
      case _ => throw new IllegalArgumentException(s"Invalid string parameter $j")
    }
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else if(relaxed && (j.isInstanceOf[JsBoolean] 
                                                 || j.isInstanceOf[JsNumber])){ None }
                               else if ((j == JsNull) && (default.isDefined || !required)) { None }
                               else { Some(s"Expected a string for $name") }
  override def getDefault: JsValue = 
    Json.toJson(default)
}

