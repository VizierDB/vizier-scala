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

import play.api.libs.json._

object JsonUtils
{

  def prettyJsPath(path: JsPath): String =
  {
    path.path.foldLeft("$") { 
      case (accum, IdxPathNode(idx)) => 
        accum+"["+idx+"]"
      case (accum, KeyPathNode(key)) => 
        accum+"."+key
      case (accum, recur:RecursiveSearch) => 
        accum+"."+recur.key+"*"
    }
  }

  def prettyJsonParseError(exc: JsResultException): Seq[String] =
    prettyJsonParseError(exc.errors.map { 
      case (path, errors) => (path, errors.toSeq)
    }.toSeq)

  def prettyJsonParseError(errors: Seq[(JsPath, Seq[JsonValidationError])]): Seq[String] =
    errors.flatMap { 
      case (path, errors) => 
        val prettyPath = s"At ${prettyJsPath(path)}: "
        errors.flatMap { err => 
          err.messages.map {
            prettyPath + _
          }
        }
    }
}

