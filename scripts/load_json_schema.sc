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
  def indent(str: String, by: String): String =
    str.split("\n").map { by+_ }.mkString("\n")
  def comment(description: Option[String]): String =
    description.map { comment(_) }.getOrElse("")
  def comment(description: String): String =
    "/**\n"+description.split("\n").map { " * "+_+"\n" }.mkString+" **/"
  def ujsonToPlayCode(v: ujson.Value): String = 
    v match {
      case ujson.Str(str) => s"JsString(\"$str\")"
      case ujson.Bool(bool) => s"JsBoolean($bool)"
      case ujson.Num(num) => s"JsNumber(BigDecimal($num))"
    }
  def ujsonToPlayMatcher(v: ujson.Value): String = 
    v match {
      case ujson.Str(str) => s"JsString(\"$str\")"
      case ujson.Bool(bool) => s"JsBoolean($bool)"
      case ujson.Num(num) => s"JsNumber(v) if v.toDouble == $num"
    }
  def ujsonToLabel(v: ujson.Value): String = 
    v match {
      case ujson.Str(str) => str
      case ujson.Bool(bool) => bool.toString
      case ujson.Num(num) => num.toString
    }

  val IsInternalType = Set(
    "String",
    "JsNumber",
    "JsValue",
    "JsNull.type",
    "Boolean"
  )

  def sanitizeLabel(str: String): String =
    capsCase(str.replaceAll("[^a-zA-Z0-9]+", "_")
                .replace("_+$", "")) match {
      case x if IsInternalType(x) => x+"Const"
      case x => x
    }
}

import UtilityFunctions._

sealed trait ScalaDataType
{
  def nameHint: String
  def scalaType: String
  def decodeJson(from: String): String
  def decodeJsonOpt(from: String): String
  def encodeJson(from: String): String
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType]
  def accumulateDependentTypes(
    accum: Set[ScalaDataType], 
    typeLookup: PartialFunction[String, ScalaDataType]
  ): Set[ScalaDataType] =
  {
    if(accum contains this){ return accum }
    dependentTypes(typeLookup)
      .foldLeft(accum + this) { (a, t) => 
        t.accumulateDependentTypes(a, typeLookup) 
      }
  }

}
sealed trait BaseType extends ScalaDataType
sealed trait SimpleBaseType extends BaseType
{
  def decodeJson(from: String): String = 
    s"$from.as[$scalaType]"
  def decodeJsonOpt(from: String): String = 
    s"$from.asOpt[$scalaType]"
  def encodeJson(from: String): String = 
    s"Json.toJson($from)"  
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set.empty
}
sealed trait CompositeBaseType extends BaseType
{
  def decodeJson(from: String): String = 
    s"${nameHint}Codec.decode($from)"
  def decodeJsonOpt(from: String): String = 
    s"${nameHint}Codec.decodeOpt($from)"
  def encodeJson(from: String): String = 
    s"${nameHint}Codec.encode($from)"
  def renderCodecs: String
}
sealed trait GlobalConstantType extends SimpleBaseType
{
  def value: String
  override def decodeJson(from: String): String = 
    s"$value"
  override def decodeJsonOpt(from: String): String = 
    s"$from.asOpt[$scalaType].map { _ => $value }"
}
sealed trait ReferencedType extends ScalaDataType
{ 
  def label: String
  def nameHint = label
  def scalaType = label
  def description: Option[String] 
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String
  def renderCodecs: String
  def decodeJson(from: String): String = 
    s"${scalaType}Codec.decode($from)"
  def decodeJsonOpt(from: String): String = 
    s"${scalaType}Codec.decodeOpt($from)"
  def encodeJson(from: String): String = 
    s"${scalaType}Codec.encode($from)"

}

case object AnyType extends SimpleBaseType
{
  def nameHint = "Any"
  def scalaType = "JsValue"
}

case object StringType extends SimpleBaseType
{
  def nameHint = "String"
  def scalaType = "String"
}
case object NumberType extends SimpleBaseType
{
  def nameHint = "Number"
  def scalaType = "JsNumber"
}
case object BooleanType extends SimpleBaseType
{
  def nameHint = "Bool"
  def scalaType = "Boolean"
}
case object NullType extends GlobalConstantType
{
  def nameHint = "Null"
  def scalaType = "JsNull.type"
  def value = "JsNull"
}
case object EmptyObject extends GlobalConstantType
{
  def nameHint = "EmptyObject"
  def scalaType = "JsObject"
  def value = "Json.obj()"
}

case class OptionType(baseType: ScalaDataType) extends SimpleBaseType
{
  def nameHint = 
    if(baseType.isInstanceOf[OptionType]){ baseType.nameHint }
    else { "MaybeA"+baseType.nameHint }
  def scalaType = 
    if(baseType.isInstanceOf[OptionType]){ baseType.scalaType }
    else { "Option["+baseType.scalaType+"]" }

  override def decodeJson(from: String): String = 
    if(baseType.isInstanceOf[OptionType]){ baseType.decodeJson(from) }
    else { baseType.decodeJsonOpt(from) }
  override def decodeJsonOpt(from: String): String = 
    if(baseType.isInstanceOf[OptionType]){ baseType.decodeJsonOpt(from) }
    else { baseType.decodeJsonOpt(from)+".map { Some(_) }" }
  override def encodeJson(from: String): String = 
    if(baseType.isInstanceOf[OptionType]){ baseType.encodeJson(from) }
    else { s"$from.map { optionalValue => ${baseType.encodeJson("optionalValue")} }" }
  override def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = 
    if(baseType.isInstanceOf[OptionType]){ baseType.dependentTypes(typeLookup) }
    else { Set(baseType) }
}

case class ArrayType(of: ScalaDataType) extends CompositeBaseType
{
  def nameHint = "ArrayOf"+of.nameHint
  def scalaType = "Seq["+of.scalaType+"]"
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set(of)
  def renderCodecs: String =
    s"""object ${nameHint}Codec {
       |  def decode(j: JsValue): $scalaType =
       |    decodeOpt(j).get
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    Some(j.as[Seq[JsValue]].map { x => 
       |      ${of.decodeJsonOpt("x")}.getOrElse { return None } })
       |  def encode(j: $scalaType): JsArray =
       |      JsArray(j.map { x => ${of.encodeJson("x")} })
       |}""".stripMargin    
}

case class MapType(of: ScalaDataType) extends CompositeBaseType
{
  def nameHint = "DictOf"+of.nameHint
  def scalaType = "Map[String,"+of.scalaType+"]"
  override def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set(of)
  def renderCodecs: String =
    s"""object ${nameHint}Codec {
       |  def decode(j: JsValue): $scalaType =
       |    decodeOpt(j).get
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    Some(j.as[Map[String,JsValue]].mapValues { x => 
       |      ${of.decodeJsonOpt("x")}.getOrElse { return None } })
       |  def encode(j: $scalaType): JsObject =
       |      JsObject(j.mapValues { x => ${of.encodeJson("x")} })
       |}
       |""".stripMargin    
}

case class TypeRef(label: String) extends SimpleBaseType
{
  def nameHint = label
  def scalaType = label
  override def decodeJson(from: String): String = 
    s"${scalaType}Codec.decode($from)"
  override def decodeJsonOpt(from: String): String = 
    s"${scalaType}Codec.decodeOpt($from)"
  override def encodeJson(from: String): String = 
    s"${scalaType}Codec.encode($from)"
  override def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = 
    typeLookup.lift(label).toSet
}

case class UnionType(
  label: String, 
  of: Seq[BaseType], 
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    // println(of.mkString("\n"))
    assert(of.indexWhere { _.scalaType == label } < 0, s"Union includes itself: ${of.mkString(", ")}")
    s"""${comment(description)}
        |sealed trait $label ${inheritance}
        |${
          of.map { 
            case t:TypeRef            => s"// ${t.label} is defined elsewhere"
            case t:GlobalConstantType => s"case object ${label}As${t.nameHint} extends $label"
            case t:BaseType           => s"case class ${label}As${t.nameHint}(value: ${t.scalaType}) extends $label"
          }.map { "   " + _ }.mkString("\n")
        }
        |""".stripMargin
  }
  def renderCodecs: String =
    s"""object ${label}Codec {
       |  def decode(j: JsValue): ${scalaType} =
       |    decodeOpt(j).get
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    ${of.map {
          case t:GlobalConstantType => s"${t.decodeJsonOpt("j")}.map { _ => "+label+"As"+t.nameHint+" }" 
          case t:TypeRef            => s"${t.decodeJsonOpt("j")}" 
          case t:BaseType           => s"${t.decodeJsonOpt("j")}.map { ${label}As${t.nameHint}(_) }"
        }.mkString(".orElse {\n    ")++" "+("} "*(of.size-1))}
       |  def encode(j: $scalaType): JsValue =
       |    j match {
       |      ${of.map {
          case t:GlobalConstantType => s"case ${label}As${t.nameHint} /* Global, ${t.getClass.getSimpleName} */ => ${t.value}"
          case t:TypeRef            => s"case x:${t.label} /* TypeRef */ => ${t.encodeJson("x")}" 
          case t:ScalaDataType      => s"case ${label}As${t.nameHint}(x) /* Base, ${t.getClass.getSimpleName} */ => ${t.encodeJson("x")}"
        }.mkString("\n      ")}
       |    }
       |}""".stripMargin
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = of.toSet
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
  def renderCodecs: String =
  {
    baseType match {
      case ref: ReferencedType if ref.label == label => 
        s"// see ${ref.label} (${baseType.getClass.getSimpleName()})"
      case TypeRef(ref) if ref == label => 
        throw new IllegalArgumentException(s"Can't render $ref")
      case ref: BaseType =>       
        s"""object ${label}Codec {
           |  def decode(j: JsValue): ${scalaType} =
           |    decodeOpt(j).get\n
           |  def decodeOpt(j: JsValue): Option[$scalaType] =
           |    try {
           |      ${baseType.decodeJsonOpt("j")}
           |        .map { $label(_) }
           |    } catch {
           |      case _:AssertionError => None
           |    }
           |  def encode(j: $scalaType): JsValue =
           |    ${baseType.encodeJson("j.value")}
           |}""".stripMargin
    }
  }
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set(baseType)
}
case class EnumType(
  label: String, 
  baseType: ScalaDataType, 
  values: Seq[ujson.Value],
  description: Option[String],
) extends ReferencedType
{
  def symbolForUJson(v: ujson.Value):String =
    label+capsCase(sanitizeLabel(ujsonToLabel(v)))

  def valueCaseObjects =
    values.map {
      case ujson.Null => 
        s"case object ${label}Undefined extends $label { val payload = JsNull }"
      case v => 
        s"case object ${symbolForUJson(v)} extends $label { val payload = ${ujsonToPlayCode(v)} }"

    }

  def valuePayloadMatchers =
    values.map {
      case ujson.Null => 
        s"case JsNull => Some(${label}Undefined)"
      case v =>
        s"case ${ujsonToPlayMatcher(v)} => Some(${symbolForUJson(v)})"
    }

  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    assert(constraints.isEmpty)
    s"""${comment(description)}
       |sealed trait $label $inheritance { val payload: JsValue }
       |${valueCaseObjects.map { "   " + _ }.mkString("\n")}
       |""".stripMargin
  }
  def renderCodecs: String =
    s"""object ${label}Codec {
       |  def decode(j: JsValue): ${scalaType} =
       |    decodeOpt(j).get
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    j match {
       |${valuePayloadMatchers.map { "      " + _  }.mkString("\n")}
       |      case _ => None
       |    }
       |  def encode(j: $scalaType): JsValue =
       |    j.payload
       |}
       |""".stripMargin
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set.empty
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
    s"""${comment(description)}
       |case class ${label}() $inheritance
       |""".stripMargin
       // |{
       // |  implicit val format = Format[$label](
       // |    new Reads[$label] { 
       // |      def reads(j: JsValue): JsResult[$label] =
       // |    },
       // |    new Writes[$label] { 
       // |      def writes(j: JsValue) = 
       // |    },
       // |  )
       // |}
  }
  def renderCodecs = 
    s"""object ${label}Codec {
       |  def decode(j: JsValue): ${scalaType} =
       |    decodeOpt(j).get\n
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    j match {
       |      case ${ujsonToPlayMatcher(payload)} => Some($label())
       |      case _ => None
       |    }
       |  def encode(j: $scalaType): JsValue =
       |    ${ujsonToPlayCode(payload)}
       |}
       |""".stripMargin
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = Set.empty
}
case class StructType(
  label: String, 
  requiredFields: Set[String],
  fields: Map[String, (Option[String], ScalaDataType)], 
  optionalProperties: Option[ScalaDataType],
  description: Option[String],
) extends ReferencedType
{
  def renderClass(inheritance: String, constraints: Seq[DataConstraint]): String =
  {
    s"""${comment(description)}
       |case class $label(
       |${(
        fields.map { case (fieldName, (fieldDescription, fieldType)) =>
          fieldDescription.map { x => indent(comment(x), "  ")+"\n  " }
                          .getOrElse("  ")+
          s"var `$fieldName` : "+
          (
            if(requiredFields(fieldName) || fieldType.isInstanceOf[OptionType]) { 
              fieldType.scalaType 
            } else { 
              "Option["+fieldType.scalaType+"]" 
            }
          )+
          (if(!requiredFields(fieldName) || fieldType.isInstanceOf[OptionType]) { " = None" } else { "" })
        }++optionalProperties.map { optional =>
          s"\n  /**\n   * optional parameters for $label\n   */\n  var optionalFields: Map[String,${optional.scalaType}]"
        }
        ).mkString(",\n")}
       |) $inheritance 
       |{ ${constraints.map { _.assertion }.mkString("\n  ")} }
       |object $label {
       |  val definedFields = Set(${fields.keySet.map { "\""+_+"\"" }.mkString(", ") }) 
       |}
       |""".stripMargin
  }
  def renderCodecs: String = 
  {
    val fieldList = 
      fields.toSeq.map { case (name, (_, dataType)) => name -> dataType } 
    def optionalPropertyDecoder = optionalProperties.toSeq.flatMap { case optional =>
      Seq(
        s"optionalFields = j.as[Map[String, JsValue]].toSeq.filterNot {",
        s"    x => $label.definedFields(x._1)",
        s"  }.flatMap { case (name, value) =>",
        s"    ${optional.decodeJsonOpt("value")}.map { name -> _ }",
        s"  }.toMap",
      )
    }.toSeq
    s"""object ${label}Codec {
       |  def decode(j: JsValue): ${scalaType} =
       |    decodeOpt(j).get\n
       |  def decodeOpt(j: JsValue): Option[$scalaType] =
       |    ${
          if(fieldList.forall { case (name, dataType) => 
            !requiredFields(name) || dataType.isInstanceOf[OptionType]
          }) {
            ( Seq("Some("+label+"(")++
              fieldList.map { case (name, dataType) =>
                s"  `$name` = (j \\ \"$name\").asOpt[JsValue].flatMap { x => "+(
                  if(dataType.isInstanceOf[OptionType]) { 
                    dataType.decodeJsonOpt("x")+".flatten"
                  } else {
                    dataType.decodeJsonOpt("x")
                  }
                )+" },"
              }++
              optionalPropertyDecoder++
              Seq(
                "))"
              )
            ).mkString("\n    ")
          } else {
            ( Seq("{") ++
              fieldList.zipWithIndex.flatMap { case ((name, dataType), idx) =>
                Seq(
                  s"  val `$name` = (j \\ \"$name\").asOpt[JsValue].flatMap { x => "+(
                    if(dataType.isInstanceOf[OptionType]) { 
                      dataType.decodeJsonOpt("x")+".flatten"
                    } else {
                      dataType.decodeJsonOpt("x")
                    }
                  )+" }"
                )++(if(requiredFields(name)){
                  Seq(s"  if(`$name`.isEmpty) { return None }")
                } else { None })
              }++
              Seq(
                s"  return Some($label("
              )++
              fieldList.zipWithIndex.map { case ((name, dataType), idx) => 
                if(requiredFields(name) && !dataType.isInstanceOf[OptionType]){
                  s"    `$name` = `$name`.get,"
                } else {
                  s"    `$name` = `$name`,"
                }
              }++
              optionalPropertyDecoder++
              Seq(
                "  ))",
                "}",
              )
            ).mkString("\n    ")
          }}
       |
       |  def encode(j: $scalaType): JsObject =
       |    ${
            (
              Seq("JsObject(","  Seq[Option[(String,JsValue)]](")++
              fields.map { 
                case (name, (_, OptionType(dataType))) =>
                  s"    j.`$name`.map { x => \"$name\" -> ${dataType.encodeJson("x")} },"
                case (name, (_, dataType)) if requiredFields(name) =>
                  s"    Some(\"$name\" -> ${dataType.encodeJson(s"j.`$name`")}),"
                case (name, (_, dataType)) =>
                  s"    j.`$name`.map { x => \"$name\" -> ${dataType.encodeJson("x")} },"
              }++
              Seq("  ).flatten.toMap")++
              optionalProperties.map { optional => 
                s"  ++j.optionalFields.mapValues { x => ${optional.encodeJson("x")} }"
              }++
              ")"
            ).mkString("\n    ")

       }
       |}
       |""".stripMargin
  }
  def dependentTypes(typeLookup: PartialFunction[String, ScalaDataType]): Set[ScalaDataType] = 
    (fields.values.map { _._2 } ++ optionalProperties).toSet
}


sealed trait DataConstraint
{ def assertion: String }
sealed trait NumberDataConstraint extends DataConstraint
sealed trait ArrayDataConstraint extends DataConstraint
sealed trait MapDataConstraint extends DataConstraint
sealed trait StringDataConstraint extends DataConstraint
case class NumberIsLessThanOrEqual(value: ujson.Num) extends NumberDataConstraint
{
  def assertion = s"assert(value.value <= $value)"
}
case class NumberIsGreaterThanOrEqual(value: ujson.Num) extends NumberDataConstraint
{
  def assertion = s"assert(value.value >= $value)"
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
case class StringMatchesRegexp(re: String, label: String) extends StringDataConstraint
{
  def assertion = s"val $label = \"$re\".r; assert($label.unapplySeq(value).isDefined)"
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

  def registerExternalWrapper(
    name: String, 
    base: ScalaDataType,
    description: String = null,
    constraints: Seq[DataConstraint] = Seq.empty
  ) =
    classCache.put(name, ConstrainedType(name, base, constraints, Option(description)))

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
    "type"
  )++SupportedConstraintTypes

  def flattenAnyOf(
    obj: ujson.Obj, 
    target: ujson.Obj = ujson.Obj("anyOf" -> ujson.Arr())
  ): ujson.Obj =
  {
    if(obj.value.contains("anyOf")){
      for(elem <- obj("anyOf").arr.value)
      {
        elem match {
          case j:ujson.Obj if j.value.contains("anyOf") => flattenAnyOf(j, target)
          case _ => target("anyOf") = target("anyOf").arr.value :+ elem
        }
      }
    }
    for((key, elem) <- obj.value)
    {
      if(key != "anyOf"){
        target.value(key) = elem
      }
    }
    return target
  }

  def parseAnyOf(obj: ujson.Obj): ScalaDataType =
  {
    println(obj)
    assert( (obj.value.keySet -- SupportedAnyOfFields).isEmpty,
            s"Unsupported AnyOf fields in schema: ${(obj.value.keySet -- SupportedAnyOfFields).mkString(",")}\n${ujson.write(obj, indent=4)}")
    val name = stackTop()
    def nameHint(optionObj: ujson.Obj): String =
    {
      if(optionObj.value.contains("$ref")){ null }
      else if(optionObj.value.contains("const")){ 
        optionObj("const") match {
          case ujson.Str(x) => x
          case ujson.Bool(x) => x.toString
        }
      }
      else if(optionObj.value.contains("format")){ optionObj("format").str }
      else if(optionObj.value.contains("type")){ name+"As"+capitalize(optionObj("type").str) }
      else { 
        throw new IllegalArgumentException(s"Need to guess name for any type $optionObj")
      }
    }
    val options:Seq[BaseType] = 
      obj("anyOf").arr.value.toSeq.flatMap { 
        case optionObj:ujson.Obj if optionObj.value.contains("type") 
                                  && optionObj("type").isInstanceOf[ujson.Arr] =>
          optionObj("type").arr.value.map { dataType =>
            val component = ujson.Obj(optionObj.value ++ Map("type" -> dataType))
            parseType(component, nameHint = "As"+capitalize(dataType.str)) 
          }
        case optionObj => 
          Seq(parseType(optionObj, nameHint = nameHint(optionObj.obj)))
      }.toSet.toSeq

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
        case o:ujson.Obj => Some(parseType(o, nameHint = name+"Optional"))
      }

    if(obj.value.contains("properties")){
      val allFields:Map[String,(Option[String],ScalaDataType)] =
        obj("properties")
           .obj.value
           .map { case (field, fieldType) =>
              (field, (
                hydrate(fieldType).obj.value.get("description").map { _.str },
                parseType(fieldType, nameHint = name+capsCase(field), mode = "FIELD") 
              ))
            }
           .toMap

      return withConstraints(obj,
        StructType(
          label = name,
          description = obj.value.get("description").map { _.str },
          requiredFields = requiredFields,
          fields = allFields,
          optionalProperties = optionalProperties,
        )
      )
    } else if(optionalProperties.isDefined){
      return withConstraints(obj,
        MapType(optionalProperties.get)
      )
    } else {
      return EmptyObject
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
      if(obj.value.contains("items")){
        ArrayType(parseType(obj("items"), nameHint = stackTop() + "Element"))
      } else {
        ArrayType(AnyType)
      }
    )
  }

  def parseType(base: ujson.Value, nameHint: String = null, mode: String = "OBJECT"): BaseType =
  {

    var name = sanitizeLabel(nameOf(
      base, 
      hint = Option(nameHint)
                .getOrElse { if(stack.isEmpty) { "Root" } else { stackTop() }}
    ))
    pushStack(mode, name + (if(classCache contains name){" *" }else{""}))
    stackNote(base.toString)
    try 
    {
      if( !(classCache contains name) )
      {
        // Create a placeholder in case of loops
        classCache(name) = UnionType("DO_NOT_USE_PLACEHOLDER", Seq(), None)
        val parsedType = hydrate(base) match {
          case obj: ujson.Obj if (obj.value.keySet -- Set("description")).isEmpty => 
            AnyType

          case obj: ujson.Obj if obj.value.contains("anyOf") =>
            parseAnyOf(flattenAnyOf(obj))
          
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
        def checkRecursion =
          assert(!classCache.contains(name) || 
                 classCache(name).label != "DO_NOT_USE_PLACEHOLDER",
                 "Didn't properly clean up class cache")
        parsedType match {
          case r:ReferencedType => classCache(name) = r; assert(r.label == name); checkRecursion; return TypeRef(name)
          case r:BaseType       => classCache.remove(name); checkRecursion; return r
          case _ => throw new RuntimeException("Unexpected Data Type")
        }
      } else {
        return TypeRef(name)
      }
    } finally {
      popStack()
    }
  }

  lazy val rootType = parseType(root)


  def renderSchema(out: Writer) = 
  {
    rootType
    // parseType(apply("#/definitions/TitleAnchor"))
    // out.write(s"\n////////////////////////////////////////////////////////////////////////\n")
    // out.write("// Creating forward references to Json Formatters\n")
    // for(clazz <- classCache.keys){
    //   out.write(s"import $clazz.{ format => ${clazz}Format }\n")
    // }
    for(clazz <- classCache.values){
      out.write(s"\n////////////////////////////////////////////////////////////////////////\n")
      out.write(s"\n// ${clazz.label} (${clazz.getClass.getSimpleName})\n")
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
      out.flush()
    }
    out.flush()
  }


  def renderCodecs(out: Writer) =
  {
    for(clazz <- rootType.accumulateDependentTypes(Set.empty, classCache)){
      def dumpHeader = 
      {
        out.write(s"\n////////////////////////////////////////////////////////////////////////")
        out.write(s"\n// ${clazz.nameHint} (${clazz.getClass.getSimpleName})\n")
        out.flush()
      }
      // out.write(clazz.renderParser)
      // out.write(s"// $clazz")
      clazz match {
        // Uncached typeRefs are references to external objects.  Use Play for these
        case t:TypeRef if !classCache.contains(t.label) =>
        {
          dumpHeader
          out.write(
            s"""object ${t.label}Codec {
               |  def decode(j: JsValue): ${t.scalaType} = j.as[${t.label}]
               |  def decodeOpt(j: JsValue): Option[${t.scalaType}] = j.asOpt[${t.label}]
               |  def encode(j: ${t.scalaType}): JsValue = Json.toJson(j)
               |}
               |""".stripMargin
          )
        }
        case _:TypeRef | _:OptionType | _:SimpleBaseType => ()// These are special-cased in the encoders above
        case t: ReferencedType => 
        {
          dumpHeader
          out.write(t.renderCodecs)
          out.write("\n")
          out.flush()
        }
        case t:CompositeBaseType => 
          dumpHeader
          out.write(t.renderCodecs)
          out.write("\n")
          out.flush()
      }
    }
  }
}



def renderHeader(output: Writer): Unit =
{
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/*** THIS FILE IS AUTOMATICALLY GENERATED ***/\n")
  output.write("/***   (by scripts/load_json_schema.sc)   ***/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/***                                      ***/\n")
  output.write("/***        DO NOT MODIFY THIS FILE       ***/\n")
  output.write("/***                                      ***/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("/********************************************/\n")
  output.write("\n")
}

val vegaDir =
  os.pwd / os.up / "vizier" / "vega-src" / "info" / "vizierdb" / "vega"

val vegalite = 
  new JsonSpec(vegaDir / "vega-lite-v5.2.0.json")

vegalite.registerExternalWrapper("UriReference", TypeRef("Uri"), description = "A URI")
vegalite.registerExternalWrapper("ColorHex", StringType, description = "A hex color code (e.g., #ffffff)", 
                                 constraints = Seq(StringMatchesRegexp("#([0-9A-Fa-f]{3})+", "HEX")))

val vegasrc = 
  vegaDir / "schema.scala"

def ignore(x: => Unit): Unit = {}

///// SCHEMA
{
  val output = new java.io.FileWriter((vegaDir / "vega-schema.scala").toString)
  // val output = new java.io.OutputStreamWriter(System.out)
  output.write("package info.vizierdb.vega\n")
  renderHeader(output)
  output.write("import play.api.libs.json._\n")
  output.write("import info.vizierdb.vega.ExternalSupport._\n")
  vegalite.renderSchema(output)
  output.flush()
}

///// PARSERS
{
  val output = new java.io.FileWriter((vegaDir / "vega-play-codecs.scala").toString)
  // val output = new java.io.OutputStreamWriter(System.out)
  output.write("package info.vizierdb.vega\n")
  renderHeader(output)
  output.write("import play.api.libs.json._\n")
  output.write("import info.vizierdb.vega.ExternalSupport._\n")
  output.write(comment("Play Json Encoders/Decoders for Vega operations")+"\n")
  vegalite.renderCodecs(output)
  output.flush()
}