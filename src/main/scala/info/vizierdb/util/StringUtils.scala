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

  def oxfordComma(elements: Seq[String], op: String = "and"): String =
    elements match {
      case Seq() => ""
      case Seq(a) => a
      case Seq(a, b) => s"$a $op $b"
      case _ => 
      {
        val reversed = elements.reverse
        (reversed.tail.reverse :+ s"$op ${reversed.head}").mkString(", ")
      }
    }
}

