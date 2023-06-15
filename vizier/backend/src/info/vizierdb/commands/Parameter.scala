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
import info.vizierdb.types.LanguageType
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.spark.SparkSchema
import org.apache.spark.sql.types.DataType
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.catalog.PythonVirtualEnvironment

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
      if(required) { Some(s"Missing parameter for parameter $name") }
      else { None }
    } else { doValidate(j: JsValue) }
  def doValidate(j: JsValue): Iterable[String]
  def encode(v: Any): JsValue
  def describe(parent: Option[String], index: Int): Seq[serialized.ParameterDescription] =
    Seq(serialized.SimpleParameterDescription(
      id = id,
      name = name,
      datatype = datatype,
      hidden = hidden,
      required = required,
      parent = parent,
      index = index,
      default = Some(getDefault)
    ))
  def convertToProperty(j: JsValue): JsValue = j
  def convertFromProperty(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = j

  /**
   * Recursively replace the specified parameter
   */
  def replaceParameterValue( 
    arg: JsValue, 
    rule: PartialFunction[(Parameter, JsValue),JsValue]
  ): Option[JsValue] =
    rule.lift.apply(this, arg)
}

object Parameter
{

  def describe(list: Seq[Parameter]): Seq[serialized.ParameterDescription] =
    doDescribe(list, 0, None)._1

  def doDescribe(
    list: Seq[Parameter], 
    startIdx: Int, 
    parent: Option[String]
  ): (Seq[serialized.ParameterDescription], Int) =
  {
    list.foldLeft( (Seq[serialized.ParameterDescription](), startIdx) ) { 
      case ((accum, idx), curr) =>
        val currDescription = curr.describe(parent, idx)
        (accum ++ currDescription, idx + currDescription.size)
    }
  }

}

trait StringEncoder
{
  def name: String
  def encode(v: Any): JsValue = 
    v match {
      case j:JsValue => j
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
      case j:JsValue => j
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
      case j:JsValue => j
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

case class JsonParameter(
  id: String,
  name: String,
  default: Option[JsValue] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter
{
  def datatype = "json"
  def doStringify(j: JsValue): String = j.toString
  def doValidate(j: JsValue): Iterable[String] = Seq.empty
  override def getDefault: JsValue = 
    default.getOrElse { JsNull }
  def encode(v: Any): JsValue =
    v match {
      case j:JsValue => j
      case s: String => JsString(s)
      case b: Boolean => JsBoolean(b)
      case l: Long => JsNumber(l)
      case i: Integer => JsNumber(i.toInt)
      case f: Float => JsNumber(f)
      case d: Double => JsNumber(d)
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
      case j:JsValue => j
      case x:Boolean => JsBoolean(x)
      case _ => throw new VizierException("Invalid Parameter to $name (expected Boolean)")
    }
}

case class DataTypeParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false  
) extends Parameter
{
  def datatype = "datatype"
  def doStringify(j: JsValue): String = j.as[DataType].toString()
  def doValidate(j: JsValue) = 
    if(j.asOpt[DataType].isDefined) { None }
    else { Some("Expected a datatype for $name")}
  def encode(v: Any): JsValue = 
    v match {
      case j: JsValue => j
      case s: String => Json.toJson(SparkSchema.decodeType(s))
      case Some(x) => encode(x)
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Datatype, but got $v)")
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
 
  override def describe(parent: Option[String], index: Int) = 
    Seq(serialized.CodeParameterDescription(
      id = id,
      name = name,
      datatype = datatype,
      hidden = hidden,
      required = required,
      parent = parent,
      index = index,
      default = Some(getDefault),
      language = language
    ))
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
  override def describe(parent: Option[String], index: Int) = 
    Seq(serialized.ArtifactParameterDescription(
      id = id,
      name = name,
      datatype = datatype,
      hidden = hidden,
      required = required,
      parent = parent,
      index = index,
      default = Some(getDefault),
      artifactType = artifactType
    ))
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

case class CachedStateParameter(
  id: String,
  name: String,
  default: Option[Long] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "cache"
  def doStringify(j: JsValue): String = j.as[Long].toString
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else if ((j == JsNull) && (default.isDefined || !required)) { None }
                               else { Some(s"Expected a number for $name") }
  override def getDefault: JsValue = 
    default.map { JsNumber(_) }.getOrElse { JsNull }
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
      case iter:Iterable[Any] => {
        val elems = iter.toSeq
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
      case _ => throw new VizierException(s"Invalid Parameter to $name (got ${v.getClass.getSimpleName}, but expected Seq)")
    }
  override def convertToProperty(j: JsValue): JsValue = 
  {
    Json.toJson(
      j.as[Seq[Map[String,JsValue]]].map { row =>
        components.map { param => 
          val v = row.getOrElse(param.id, param.getDefault)
          serialized.CommandArgument(id = param.id, value = param.convertToProperty(v))
        }
      }
    )
  }
  override def convertFromProperty(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = 
  {
    JsArray(
      j.as[Seq[serialized.CommandArgumentList.T]].map { case row => 
        val arguments = serialized.CommandArgumentList.toMap(row)
        JsObject(
          components.flatMap { param => 
            arguments.get(param.id)
                     .map { v => 
                        param.id -> 
                          param.convertFromProperty(
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
  override def describe(parent: Option[String], index: Int): Seq[serialized.ParameterDescription] =
  {
    super.describe(parent, index) ++ Parameter.doDescribe(components, index+1, Some(id))._1
  }

  override def getDefault: JsValue = JsArray(Seq())
  override def replaceParameterValue( 
    arg: JsValue, 
    rule: PartialFunction[(Parameter, JsValue),JsValue]
  ): Option[JsValue] =
  {
    var forceNonNoneReturn = false
    val base: Seq[Map[String, JsValue]] = 
      super.replaceParameterValue(arg, rule)
           .flatMap { x => forceNonNoneReturn = true;
                       x.asOpt[Seq[Map[String, JsValue]]] }
           .getOrElse { Seq.empty }
    val listReplacements: Seq[Option[JsObject]] = 
      base.map { lineArgs =>
        val lineReplacements = 
          components.map { param =>
            param.name -> 
              param.replaceParameterValue(lineArgs.getOrElse(param.name, JsNull), rule)
          }.filter { _._2.isDefined }
           .map { x => x._1 -> x._2.get }
           .toMap
        if(lineReplacements.isEmpty) { None }
        else {
          Some(
            JsObject(
              lineArgs.map { case (k, v) => 
                k -> lineReplacements.getOrElse(k, v)
              }.toMap
            )
          )
        }
      }
    if(listReplacements.flatten.isEmpty || !forceNonNoneReturn){ None }
    else {
      Some(
        JsArray(
          listReplacements.zip(base).map { case (replacement, original) => 
            replacement.getOrElse(JsObject(original))
          }.toSeq
        )
      )
    }
  }
}

case class RecordParameter(
  id: String,
  name: String,
  components: Seq[Parameter],
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter
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
  override def convertToProperty(j: JsValue): JsValue = 
  {
    val record = j.as[Map[String,JsValue]]
    Json.toJson(
      components.map { param => 
        val v = record.getOrElse(param.id, param.getDefault)
        serialized.CommandArgument(id = param.id, value = param.convertToProperty(v))
      }
    )
  }
  def encode(v: Any): JsValue = 
    v match { 
      case fields:Map[_,_] => 
        JsObject( 
          zipParameters(fields.asInstanceOf[Map[String,Any]])
            .map { case (component, subV) =>
              component.id -> 
                subV.map { component.encode(_) }
                    .getOrElse { component.getDefault }
            }.toMap
        )

      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Map)")
    }
  override def convertFromProperty(
    j: JsValue,
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x }
  ): JsValue = 
  {
    val arguments = serialized.CommandArgumentList.toMap(j.as[serialized.CommandArgumentList.T])
    JsObject(
      components.flatMap { param => 
        arguments.get(param.id)
                 .map { v => 
                    param.id -> 
                      param.convertFromProperty(
                        preprocess(param, v),
                        preprocess
                      )
                 }
      }
      .toMap
    )
  }
  override def describe(parent: Option[String], index: Int): Seq[serialized.ParameterDescription] =
  {
    super.describe(parent, index) ++ Parameter.doDescribe(components, index+1, Some(id))._1
  }

  override def replaceParameterValue(
    arg: JsValue, 
    rule: PartialFunction[(Parameter, JsValue),JsValue]
  ): Option[JsValue] =
  {
    var forceNonNoneReturn = false
    val base = 
      super.replaceParameterValue(arg, rule)
           .flatMap { x => forceNonNoneReturn = true; 
                           x.asOpt[Map[String, JsValue]] }
           .getOrElse { Map.empty }
    val lineReplacements = 
      components.map { param =>
        param.name -> 
          param.replaceParameterValue(base.getOrElse(param.name, JsNull), rule)
      }.filter { _._2.isDefined }
       .map { x => x._1 -> x._2.get }
       .toMap
    if(lineReplacements.isEmpty || !forceNonNoneReturn) { None }
    else {
      Some(
        JsObject(
          base.map { case (k, v) => 
            k -> lineReplacements.getOrElse(k, v)
          }.toMap
        )
      )
    }
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
  aliases: Map[String,String] = Map.empty,
  allowOther: Boolean = false
) extends Parameter with StringEncoder
{
  lazy val possibilities = Set(values.map { _.value }:_*) ++ aliases.keySet
  def datatype = "string"
  def doStringify(j: JsValue): String = j.as[String]
  def doValidate(j: JsValue) = 
    if(j.isInstanceOf[JsString]){ 
      if(possibilities(j.as[String]) || allowOther){ None }
      else {
        Some(s"Expected $name to be one of ${possibilities.mkString(", ")}, but got $j")
      }
    }
    else if ((j == JsNull) && (default.isDefined || !required)) { None }
    else { Some(s"Expected a string/enumerable for $name") }
  override def getDefault: JsValue = 
    Json.toJson(default.map { values(_).value })
  override def describe(parent: Option[String], index: Int) = 
    Seq(serialized.EnumerableParameterDescription(
      id = id,
      name = name,
      datatype = datatype,
      hidden = hidden,
      required = required,
      parent = parent,
      index = index,
      default = Some(getDefault),
      values = values.zipWithIndex.map { case (v, idx) => 
        serialized.EnumerableValueDescription(
          isDefault = default.map { _ == idx }.getOrElse(false),
          text = v.text,
          value = v.value
        )
      },
      allowOther = allowOther
    ))
  override def convertFromProperty(j: JsValue, preprocess: (Parameter, JsValue) => JsValue): JsValue = 
  {
    super.convertFromProperty(j, preprocess) match { 
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

case class EnvironmentParameter(
  id: String,
  name: String,
  language: LanguageType.T,
  default: Option[String] = None,
  required: Boolean = true,
  hidden: Boolean = false,
) extends Parameter
{

  override def datatype: String = "environment"

  override def doStringify(j: JsValue): String = 
    language match {
      case LanguageType.PYTHON =>
        val summary = j.as[serialized.PythonEnvironmentSummary]
        summary.name
      case _ =>
        (j \ "name").asOpt[String] match {
          case Some(n) => n
          case None => "<<Invalid Environment>>"
        }
    }

  override def doValidate(j: JsValue): Iterable[String] = 
  {
    (Seq(
      if((j \ "name").isEmpty){ Some("Name field missing") } else { None },
      if((j \ "revision").isEmpty){ Some("Revision field missing") } else { None },
    ) ++ (language match { 
      case LanguageType.PYTHON => 
        Seq(
          if((j \ "pythonVersion").isEmpty){ Some("Python Version field missing") } else { None },
        )
      case _ => Seq.empty
    })).flatten
  }

  override def encode(v: Any): JsValue = 
    language match {
      case LanguageType.PYTHON =>
        val env:serialized.PythonEnvironmentSummary = v match {
          case summary:serialized.PythonEnvironmentSummary =>
            summary
          case name:String => 
            CatalogDB.withDB { implicit s =>
              PythonVirtualEnvironment.getByName(name)
            }.summarize
          case id:Int =>
            CatalogDB.withDB { implicit s =>
              PythonVirtualEnvironment.getById(id.toLong)
            }.summarize
          case id:Long =>
            CatalogDB.withDB { implicit s =>
              PythonVirtualEnvironment.getById(id)
            }.summarize
        }
        Json.toJson(env)
    }

  override def describe(parent: Option[String], index: Int) = 
    Seq(serialized.CodeParameterDescription(
      id = id,
      name = name,
      datatype = datatype,
      hidden = hidden,
      required = required,
      parent = parent,
      index = index,
      default = Some(getDefault),
      language = language.toString()
    ))
}