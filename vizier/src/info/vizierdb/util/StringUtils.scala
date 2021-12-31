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
package info.vizierdb.util


object StringUtils
{
  def ellipsize(text: String, len: Int): String =
        if(text.size > len){ text.substring(0, len-3)+"..." } else { text }

  def camelCaseToHuman(str: String) = 
  {
    "([^A-Z])([A-Z])"
      .r
      .replaceAllIn(str, { m => m.group(1) + " " + m.group(2) })
  }

  def oxfordComma(elems: Seq[String], sep: String = "and"): String = 
    elems match {
      case Seq() => ""
      case Seq(a) => a
      case Seq(a, b) => s"$a $sep $b"
      case _ => 
        {
          val rest = elems.reverse.tail.reverse
          val last = elems.last
          s"${rest.mkString(", ")}, $sep $last"
        }
    }

  def withDefiniteArticle(str: String): String =
  {
    val firstLetter = str.toLowerCase()(0)
    if(Set('a', 'e', 'i', 'o', 'u') contains firstLetter){
      return "an "+str
    } else {
      return "a "+str
    }
  }

  def pluralize(str: String, count: Int) =
    if(count == 1){ str }
    else { plural(str) }

  def plural(str: String): String = 
    str.toLowerCase match {
      case "copy" => str.substring(0, 3)+"ies"
      case _ => str+"s"
    }

  import org.apache.spark.sql.types._
  def friendlyTypeString(dataType: DataType): String = 
  {
    dataType match {
      case StringType => "string"
      case IntegerType => "4 byte integer"
      case LongType => "8 byte integer"
      case ShortType => "8 byte integer"
      case FloatType => "single precision float"
      case DoubleType => "double precision float"
      case ArrayType(elem, _) => "array of "+plural(friendlyTypeString(elem))
      case _ => dataType.json
    }
  }
}

