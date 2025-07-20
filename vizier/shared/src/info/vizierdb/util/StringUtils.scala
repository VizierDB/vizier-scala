/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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

  def ellipsize(list: Iterable[String], len: Int): Seq[String] =
    if(list.size > len){ list.take(len-1).toSeq :+ "..." } else { list.toSeq }

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

  def capitalize(str: String): String =
    str.substring(0, 1).toUpperCase + str.substring(1).toLowerCase()

  def formatDuration(millis: Long): String = 
  {
    var t = millis
    Seq(
      (1000, 1000, "ms"),
      (120, 60, "s"),
      (120, 60, "min"),
      (36, 24, "hr")
    ).foreach { case (cutoff, scale, suffix) => 
      if(t <= cutoff) { return s"$t $suffix" }
      else { t = t / scale }
    }
    return s"$t days"
  }
}

