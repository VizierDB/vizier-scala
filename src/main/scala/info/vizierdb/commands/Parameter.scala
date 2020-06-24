package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.VizierException

sealed trait Parameter
{
  def hidden: Boolean
  def id: String
  def name: String
  def required: Boolean
  def datatype: String
  def getDefault: JsValue = JsNull

  def stringify(j: JsValue): String
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
}

trait StringEncoder
{
  def name: String
  def encode(v: Any): JsValue = 
    v match {
      case x:String => JsString(x)
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected String)")
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
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Int/Long)")
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
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Int/Long/Float/Double)")
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
  def stringify(j: JsValue): String = j.as[Boolean].toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsBoolean]){ None }
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
  def stringify(j: JsValue): String = j.as[String].toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else { Some(s"Expected a string for $name") }
}

case class ColIdParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "colid"
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
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
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else { Some(s"Expected a string/dataset id for $name") }
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
  def stringify(j: JsValue): String = j.as[Double].toString
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else { Some(s"Expected a number for $name") }
  override def getDefault: JsValue = 
    default.map { JsNumber(_) }.getOrElse { JsNull }
}

case class FileParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "fileid"
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsObject]){ None }
                               else { Some(s"Expected an object for $name") }
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
  def stringify(j: JsValue): String = j.as[Int].toString
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
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
  def stringify(j: JsValue): String = 
  {
    val rows = j.as[Seq[Map[String, JsValue]]]
    rows.map { row => 
      "<" + components.map { t => t.stringify(row(t.id)) }.mkString(",") + ">"
    }.mkString("; ")
  }
  def zipParameters[T](record: Map[String,T]): Seq[(Parameter, Option[T])] =
    components.map { component => component -> record.get(component.id) }

  def doValidate(j: JsValue): Iterable[String] = 
    if(!j.isInstanceOf[JsArray]){ return Some(s"Expected a list for $name") }
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
        if(elems.head.isInstanceOf[Map[_,_]]){
          Json.toJson(
            elems.asInstanceOf[Seq[Map[String,Any]]].map { 
              zipParameters(_).map { case (component, subV) => 
                component.id -> 
                  subV.map { component.encode(_) }.getOrElse { component.getDefault }
              }.toMap
            }
          )
        } else if(components.length == 1) {
          Json.toJson(
            elems.map { elem => Map(components.head.id -> components.head.encode(elem)) }
          )
        } else {
          throw new VizierException(s"Invalid Parameter to $name (expected Seq to contain Maps)")
        }
      }
      case _ => throw new VizierException(s"Invalid Parameter to $name (expected Seq)")
    }
}

case class RecordParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "record"
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue): Iterable[String] = None
}

case class RowIdParameter(
  id: String,
  name: String,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with IntegerEncoder
{
  def datatype = "rowid"
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
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
  def stringify(j: JsValue): String = j.toString()
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsNumber]){ None }
                               else { Some(s"Expected a number for $name") }
}

case class EnumerableValue(text: String, value: String)
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
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  lazy val possibilities = Set(values.map { _.value }:_*)
  def datatype = "string"
  def stringify(j: JsValue): String = j.as[String]
  def doValidate(j: JsValue) = 
    if(j.isInstanceOf[JsString]){ 
      if(possibilities(j.as[String])){ None }
      else {
        Some(s"Expected $name to be one of ${possibilities.mkString(", ")}, but got $j")
      }
    }
    else { Some(s"Expected a string/enumerable for $name") }
}

case class StringParameter(
  id: String,
  name: String,
  default: Option[String] = None,
  required: Boolean = true,
  hidden: Boolean = false
) extends Parameter with StringEncoder
{
  def datatype = "string"
  def stringify(j: JsValue): String = j.as[String]
  def doValidate(j: JsValue) = if(j.isInstanceOf[JsString]){ None }
                               else { Some(s"Expected a string for $name") }
}
