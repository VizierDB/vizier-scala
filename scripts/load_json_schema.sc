/**
 * Script for generating scala case classes from json schemas.  
 * 
 * Currently, this is used to support:
 *  - Vega Lite
 * 
 * Requires:
 *  - ammonite (http://ammonite.io/)
 * 
 * Run using:
 *  ```
 *   amm load_json_schema.sc
 *  ```
 */
import java.io.Writer
import scala.collection.mutable

object UtilityFunctions
{
  def capitalize(str: String) = 
    str.take(1).toUpperCase+str.drop(1)
  def capsCase(str: String) = 
    str.split("_|-").map { capitalize(_) }.mkString
  def comment(description: Option[String]): String =
    description.map { comment(_) }.getOrElse("")
  def comment(description: String): String =
    "/**\n"+description.split("\n").map { " * "+_+"\n" }.mkString+" **/"
}

import UtilityFunctions.{ capitalize, capsCase, comment }

sealed trait ScalaDataType
{
  def nameHint: String
  def scalaType: String
}
sealed trait BaseType extends ScalaDataType
sealed trait ReferencedType extends ScalaDataType
{ 
  def label: String
  def nameHint = label
  def scalaType = label
  def description: Option[String] 
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String
}

case object AnyType extends BaseType
{
  def nameHint = "Any"
  def scalaType = "JsValue"
}

case object StringType extends BaseType
{
  def nameHint = "String"
  def scalaType = "String"
}
case object NumberType extends BaseType
{
  def nameHint = "Number"
  def scalaType = "JsNumber"
}
case object BooleanType extends BaseType
{
  def nameHint = "Bool"
  def scalaType = "Boolean"
}
case object NullType extends BaseType
{
  def nameHint = "Null"
  def scalaType = "JsNull"
}

case class OptionType(baseType: ScalaDataType) extends BaseType
{
  def nameHint = "MaybeA"+baseType.nameHint
  def scalaType = "Option["+baseType.scalaType+"]"
}

case class ArrayType(of: ScalaDataType) extends BaseType
{
  def nameHint = "ArrayOf"+of.nameHint
  def scalaType = "Seq["+of.scalaType+"]"
}
case class MapType(of: ScalaDataType) extends BaseType
{
  def nameHint = "DictOf"+of.nameHint
  def scalaType = "Map[String,"+of.scalaType+"]"
}

case class TypeRef(label: String) extends BaseType
{
  def nameHint = label
  def scalaType = label
}

case class UnionType(
  label: String, 
  of: Seq[BaseType], 
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    println(of.mkString("\n"))
    assert(of.indexWhere { _.scalaType == label } < 0, s"Union includes itself: ${of.mkString(", ")}")
    s"""${comment(description)}
        |sealed trait $label ${inheritance}
        |${
          of.map { 
            case _:TypeRef        => /* Already being rendered */ None
            case NullType         => Some(s"case object ${label}AsNull extends $label")
            case t:BaseType       => Some(s"case class ${label}As${t.nameHint}(value: ${t.scalaType}) extends $label")
          }.flatten.map { "   " + _ }.mkString("\n")
        }
        |
        |object $label {
        |  implicit val format = Format[$label](
        |    new Reads[$label] {
        |      def reads(j: JsValue): JsResult =
        |${of.map { x => "        j.asOpt["+x.scalaType+"].map { JsSuccess(_) }.getOr {" }.mkString("\n")}
        |          JsError("Expected $label") ${of.map { _ => " }" }.mkString}
        |    },
        |    new Writes[$label] {
        |      def writes(j: $label): JsValue =
        |        j match {
        |${of.map { 
            case r:TypeRef        => s"          case x:${r.scalaType} => Json.toJson(x)"
            case NullType         => s"          case ${label}AsNull => JsNull"
            case t:BaseType       => s"          case x:${label}As${t.nameHint} => Json.toJson(x)"
          }.mkString("\n")}  
        |        }
        |    }
        |  )  
        |}
        |""".stripMargin
  }
}
case class ConstrainedType(
  label: String,
  baseType: ScalaDataType, 
  checks: Seq[DataConstraint],
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    baseType match {
      case ref: ReferencedType if ref.label == label => 
        ref.renderClass(inheritance, constraints ++ checks)
      case TypeRef(ref) if ref == label => 
        throw new IllegalArgumentException(s"Can't render $ref")
      case ref: BaseType => 
        s"""${comment(description)}
           |case class $label(value: ${baseType.scalaType}) $inheritance
           |{ ${checks.map { _.assertion }.mkString("\n  ")} }
           |""".stripMargin
      case _ => 
        throw new IllegalArgumentException(s"Don't know how to render $this")
    }
  }
}
case class EnumType(
  label: String, 
  baseType: ScalaDataType, 
  values: Seq[ujson.Value],
  description: Option[String],
) extends ReferencedType
{
  def valueCaseObjects =
    values.map {
      case v@ujson.Str(payload) => 
        s"case object ${label+capitalize(payload)} extends $label { val payload = JsString(\"$payload\") }"
    }

  def valuePayloadMatchers =
    values.map {
      case ujson.Str(payload) =>
        s"case JsString(\"$payload\") => JsSuccess(${label+capitalize(payload)})"
    }

  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    assert(constraints.isEmpty)
    s"""sealed trait $label $inheritance { val payload: JsValue }
       |${valueCaseObjects.map { "   " + _ }.mkString("\n")}
       |
       |object $label {
       |  implicit val format = Format[$label](
       |    new Reads {
       |      def reads(j: JsValue): JsResult =
       |        j match {
       |${valuePayloadMatchers.map { "          " + _ }.mkString("\n")}
       |          case _ => JsError("Expecting $label")
       |        }
       |    },
       |    new Writes {
       |      def writes(j: $label): JsValue = j.payload
       |    },
       |  )
       |}
       |""".stripMargin
  }
}
case class ConstantType(
  label: String,
  baseType: ScalaDataType, 
  payload: ujson.Value,
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    ???
  }
}
case class StructType(
  label: String, 
  fields: Map[String, (Option[String], ScalaDataType)], 
  optionalProperties: Option[ScalaDataType],
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    ???
  }
}


sealed trait DataConstraint
{ def assertion: String }
sealed trait NumberDataConstraint extends DataConstraint
sealed trait ArrayDataConstraint extends DataConstraint
sealed trait MapDataConstraint extends DataConstraint
case class NumberIsLessThanOrEqual(value: ujson.Num) extends NumberDataConstraint
{
  def assertion = s"assert(value <= $value)"
}
case class NumberIsGreaterThanOrEqual(value: ujson.Num) extends NumberDataConstraint
{
  def assertion = s"assert(value >= $value)"
}
case class ArrayHasAtLeastItems(value: ujson.Num) extends ArrayDataConstraint
{
  def assertion = s"assert(value.size <= $value)"
}
case class ArrayHasAtMostItems(value: ujson.Num) extends ArrayDataConstraint
{
  def assertion = s"assert(value.size >= $value)"
}
case class MapHasAtLeastProperties(value: ujson.Num) extends MapDataConstraint
{
  def assertion = s"assert(value.size <= $value)"
}
case class MapHasAtMostProperties(value: ujson.Num) extends MapDataConstraint
{
  def assertion = s"assert(value.size >= $value)"
}



class JsonSpec(val json: ujson.Value)
{
  val classCache = mutable.Map[String, ReferencedType]()
  val classInheritance = mutable.Map[String, mutable.Set[String]]()
  val stack = mutable.Stack[String]()

  def this(file: os.Path) = 
    this(ujson.read(os.read(file)))

  def apply(ref: String): ujson.Value = 
  {
    assert(ref.startsWith("#/"))
    ref.drop(2)
       .split("/")
       .foldLeft(json){
         case (arr: ujson.Arr, key) =>  
            arr.value(key.toInt)
         case (obj: ujson.Obj, key) =>  
            obj.value(key)
       }
  }

  def hydrate(base: ujson.Value): ujson.Value =
    base match {
      case obj: ujson.Obj if obj.value contains "$ref" => 
        apply(obj("$ref").str) match {
          case lookup: ujson.Obj => 
            val merged = 
              ujson.Obj(
                lookup.value ++ 
                obj.value.filter { case ("$ref", _) => false; case _ => true }
              )
            // println(s"Merged: ${ujson.write(merged)}")
            if(merged.value.contains("$ref")){ hydrate(merged) }
            else { merged }
          case x => x
        }
      case _ => 
        return base
    }

  def nameOf(base: ujson.Value, hint: String = null): String =
    base match {
      case obj: ujson.Obj if obj.value contains "$ref" => 
        return obj("$ref").str.split("/").last
      case obj: ujson.Obj if obj.value contains "format" => 
        return capsCase(obj("format").str)
      case _ if hint != null => 
        return hint
      case x => 
        throw new IllegalArgumentException(s"Can't come up with a name (with hint $hint) for:\n${ujson.write(base, indent=4)}")
    }

  def RootKeysToDrop = Set(
    "definitions",
    "$schema",
  )

  def root = ujson.Obj(json.obj.value.filterNot { x => RootKeysToDrop(x._1) })

  private def pushStack(elemType: String, name: String) = 
  {
    println(stack.drop(1).map { _ => " | " }.mkString + 
      (if(stack.size >= 1) { " +- " } else { "" }) +
      elemType + ": " + name
    )
    stack.push(name)
  }
  private def stackNote(note:String) = 
    println(stack.map { _ => " | " }.mkString+"     ("+note+")")
  private def popStack() = 
    stack.pop
  private def stackTop(off: Int = 0) =
    stack.drop(off).head


  val SupportedConstraintTypes = Set(
    "maximum",
    "minimum",
    "maxItems",
    "minItems",
    "maxProperties",
    "minProperties",
  )

  lazy val SupportedConstraintFields = Set(
    "type",
    "description",
    "anyOf",
    "items"
  )++SupportedStructFields++SupportedArrayFields++SupportedConstraintTypes

  def parseConstraints(obj: ujson.Obj): Seq[DataConstraint] =
  {
    assert( (obj.value.keySet -- SupportedConstraintFields).isEmpty,
            s"Unsupported Constraint fields in schema: ${(obj.value.keySet -- SupportedConstraintFields).mkString(",")}\n${ujson.write(obj, indent=4)}")
    Seq(
      obj.value.get("maximum").map { x => NumberIsLessThanOrEqual(x.num) },
      obj.value.get("minimum").map { x => NumberIsGreaterThanOrEqual(x.num) },
      obj.value.get("maxItems").map { x => ArrayHasAtMostItems(x.num) },
      obj.value.get("minItems").map { x => ArrayHasAtLeastItems(x.num) },
      obj.value.get("minProperties").map { x => MapHasAtMostProperties(x.num) },
      obj.value.get("minProperties").map { x => MapHasAtLeastProperties(x.num) },
    ).flatten
  }

  def withConstraints(obj: ujson.Obj, base: ScalaDataType): ScalaDataType = 
    if(obj.value.keySet.intersect(SupportedConstraintTypes).isEmpty){ base }
    else {
      ConstrainedType(
        label = stackTop(),
        baseType = base,
        checks = parseConstraints(obj),
        description = obj.value.get("description").map { _.str },
      )
    }

  val SupportedAnyOfFields = Set(
    "anyOf",
    "description",
  )++SupportedConstraintTypes

  def parseAnyOf(obj: ujson.Obj): ScalaDataType =
  {
    assert( (obj.value.keySet -- SupportedAnyOfFields).isEmpty,
            s"Unsupported AnyOf fields in schema: ${(obj.value.keySet -- SupportedAnyOfFields).mkString(",")}\n${ujson.write(obj, indent=4)}")
    val name = stackTop()
    def nameHint(optionObj: ujson.Obj): String =
    {
      if(optionObj.value.contains("format")){ optionObj("format").str }
      else if(optionObj.value.contains("type")){ name+"As"+capitalize(optionObj("type").str) }
      else { 
        throw new IllegalArgumentException(s"Need to guess name for any type $optionObj")
      }
    }
    val options = 
      obj("anyOf").arr.value.toSeq.map { optionObj =>
        parseType(optionObj, nameHint = nameHint(hydrate(optionObj).obj)) 
      }

    if(options.size == 2 && options.contains(NullType)){
      // anyOf where null is an option is a shorthand for an option type
      return withConstraints(obj, 
        OptionType(options.filterNot { _ == NullType }.head)
      )
    }

    options.foreach {
      case TypeRef(ref) => classInheritance.getOrElseUpdate(ref, { mutable.Set.empty }) += name
      case _ => ()
    }
    withConstraints(obj, 
      UnionType(
        label = name, 
        of = options,
        description = obj.value.get("description").map { _.str }
      )
    )
  }

  def parseEnum(obj: ujson.Obj): EnumType =
  {
    stackNote(obj.toString)
    EnumType(
      label = stackTop(),
      baseType = parseTypeDescriptor(obj("type")),
      values = obj("enum").arr.value.toSeq,
      description = obj.value.get("description").map { _.str },
    )
  }

  def parseConstant(obj: ujson.Obj): ConstantType =
    ConstantType(
      label = stackTop(),
      baseType = parseTypeDescriptor(obj("type")),
      payload = obj("const"),
      description = obj.value.get("description").map { _.str },
    )

  def parseTypeDescriptor(str: String): BaseType =
    str match {
      case "string" => StringType
      case "boolean" => BooleanType
      case "number" => NumberType
      case "null" => NullType
      case x => throw new IllegalArgumentException("Unsupported primitive type: ${stackTop()}:\n$obj")
    }

  def parseTypeDescriptor(obj: ujson.Value): ScalaDataType =
    obj match {
      case ujson.Str(base) => parseTypeDescriptor(base)
      case ujson.Arr(elems) => 
        UnionType(stackTop(), 
          elems.toSeq.map { e => parseTypeDescriptor(e.str) }, 
          description = None
        )
      case x => throw new IllegalArgumentException("Unsupported primitive type: ${stackTop()}:\n$obj")
    }

  def parseLiteral(obj: ujson.Obj): ScalaDataType =
    withConstraints(obj,
      parseTypeDescriptor(obj("type"))
    )

  val SupportedStructFields = Set(
    "additionalProperties",
    "properties",
    "required",
    "type",
    "description"
  )++SupportedConstraintTypes

  def parseStruct(obj: ujson.Obj): ScalaDataType =
  {
    assert(obj("type").str == "object")
    assert( (obj.value.keySet -- SupportedStructFields).isEmpty,
            s"Unsupported Object fields in schema: ${(obj.value.keySet -- SupportedStructFields).mkString(",")}\n${ujson.write(obj, indent=4)}")

    val name = stackTop()
    val requiredFields: Set[String] = 
      obj.value
         .get("required")
         .map { _.arr.value.map { _.str } }
         .getOrElse(Seq.empty)
         .toSet

    val optionalProperties = 
      obj.value.get("additionalProperties").flatMap {
        case ujson.Bool(false) => None
        case ujson.Bool(true) => Some(AnyType)
        case o:ujson.Obj => Some(parseType(o))
      }

    if(obj.value.contains("properties")){
      val allFields:Map[String,(Option[String],ScalaDataType)] =
        obj("properties")
           .obj.value
           .map { case (field, fieldType) =>
             val baseFieldType = parseType(fieldType, nameHint = capsCase(field), mode = "FIELD") 
              (field, (
                hydrate(fieldType).obj.value.get("description").map { _.str },
                if(requiredFields(field)){ baseFieldType }
                else { OptionType(baseFieldType) }
              ))
            }
           .toMap

      return withConstraints(obj,
        StructType(
          label = name,
          description = obj.value.get("description").map { _.str },
          fields = allFields,
          optionalProperties = optionalProperties,
        )
      )
    } else if(optionalProperties.isDefined){
      return withConstraints(obj,
        MapType(optionalProperties.get)
      )
    } else {
      throw new IllegalArgumentException(s"Unsupported object structure:\n${ujson.write(obj, indent=4)}")
    }
  }

  def SupportedArrayFields = Set(
    "type",
    "description",
    "items"
  )++SupportedConstraintTypes

  def parseArray(obj: ujson.Obj): ScalaDataType =
  {
    assert(obj("type").str == "array")
    assert( (obj.value.keySet -- SupportedArrayFields).isEmpty,
            s"Unsupported Array fields in schema: ${(obj.value.keySet -- SupportedArrayFields).mkString(",")}\n${ujson.write(obj, indent=4)}")
    withConstraints(obj,
      ArrayType(parseType(obj("items"), nameHint = stackTop() + "Element"))
    )
  }

  def parseType(base: ujson.Value, nameHint: String = null, mode: String = "OBJECT"): BaseType =
  {

    var name = nameOf(
      base, 
      hint = Option(nameHint)
                .getOrElse { if(stack.isEmpty) { "Root" } else { stackTop() }}
    )
    pushStack(mode, name + (if(classCache contains name){" *" }else{""}))
    stackNote(base.toString)
    try 
    {
      if( !(classCache contains name) )
      {
        // Create a placeholder in case of loops
        classCache(name) = null
        val parsedType = hydrate(base) match {
          case obj: ujson.Obj if (obj.value.keySet -- Set("description")).isEmpty => 
            AnyType

          case obj: ujson.Obj if obj.value.contains("anyOf") =>
            parseAnyOf(obj)
          
          /////////////////////////////////////////////////////////
          
          case obj: ujson.Obj if obj.value.contains("enum") => 
            parseEnum(obj)
          
          /////////////////////////////////////////////////////////
          
          case obj: ujson.Obj if obj.value.contains("const") => 
            parseConstant(obj)
          
          /////////////////////////////////////////////////////////
          
          case obj: ujson.Obj if obj.value.contains("format") => 
            // Fields with a special format are assumed to be provided by the user
            TypeRef(capsCase(obj("format").str))
          
          /////////////////////////////////////////////////////////
          
          case obj: ujson.Obj if obj.value.contains("type") => 
            obj("type") match { 
              // Object construction is messy, break it out into its own function
              case ujson.Str("object") => 
                parseStruct(obj)

              // Array construction is messy if constraints are involved
              case ujson.Str("array") => 
                parseArray(obj)

              // Use canonical names for trivial literals
              case ujson.Str(x@("string"|"boolean"|"number"|"null")) =>
                parseLiteral(obj)

              case a:ujson.Arr => 
                parseTypeDescriptor(a)

              case x => throw new IllegalArgumentException(s"Invalid element type for '$name': '$x'\n${ujson.write(obj, indent=4)}") 
            }
          case x => throw new IllegalArgumentException(s"Invalid data for '$name'\n${ujson.write(x, indent=4)}\nbase:\n${ujson.write(base, indent=4)}")
        } 
        parsedType match {
          case r:ReferencedType => classCache(name) = r; return TypeRef(name)
          case r:BaseType => classCache.remove(name); return r
        }
      } else {
        return TypeRef(name)
      }
    } finally {
      popStack()
    }
  }

  lazy val rootType = parseType(root)


  def render(out: Writer) = 
  {
    // rootType
    parseType(apply("#/definitions/TickCount"))
    for(clazz <- classCache.values){
      out.write(s"\n/*** ${clazz.label} ***/\n")
      out.flush()
      out.write(clazz.renderClass(
        inheritance = 
          classInheritance.get(clazz.label)
                          .map { _.filterNot { _ == clazz.label } }
                          .flatMap { x => if(x.isEmpty){ None } else { Some(x) } }
                          .map { "extends "+_.mkString(" with ") }
                          .getOrElse(""),
        constraints = Seq.empty
      ))
    }
  }
}


val vegalite = 
  new JsonSpec(os.pwd / os.up / "vizier" / "schemas" / "vega-lite-v5.2.0.json")

vegalite.render(new java.io.OutputStreamWriter(System.out))
// println(vegalite.rootType)