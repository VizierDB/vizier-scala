/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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

trait Command
{
  def name: String
  def parameters: Seq[Parameter]
  def format(arguments: JsObject): String = format(Arguments(arguments.as[Map[String, JsValue]], parameters))
  def format(arguments: Arguments): String
  def process(arguments: Arguments, context: ExecutionContext): Unit

  def validate(arguments: Map[String, JsValue]): Seq[String] = 
    parameters.flatMap { parameter => 
      arguments.get(parameter.id) match {
        case Some(argument) => parameter.validate(argument)
        case None if parameter.required && parameter.getDefault.equals(JsNull) 
                            => Seq(s"Missing argument ${parameter.id} // ${parameter.getDefault}")
        case None => Seq()
      }
    }
  def encodeArguments(
      arguments: Map[String, Any], 
      base: Map[String, JsValue] = Map.empty
  ): JsObject =
    JsObject(parameters.map { parameter => 
      val encoded:JsValue =
        arguments.get(parameter.id)
                 .map { v => parameter.encode(v) }
                 .orElse { base.get(parameter.id) }
                 .getOrElse { parameter.getDefault }
      val errors = parameter.validate(encoded) 
      if(!errors.isEmpty){
        val argString = 
          arguments.get(parameter.id)
                   .map { _.toString() }
                   .getOrElse{ "<missing>" }
        throw new VizierException(
          s"Error in value '$argString' for ${parameter.name}:\n   ${errors.mkString("\n   ")}"
        )
      }
      parameter.id -> encoded
    }.toMap)
  def decodeReactArguments(
    arguments: JsValue, 
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x } 
  ): JsObject =
  {
    val saneArguments = 
      arguments.as[Seq[Map[String,JsValue]]]
               .map { arg => 
                 arg("id").as[String] -> arg.getOrElse("value", JsNull)
               }
               .toMap

    JsObject(
      parameters.flatMap { param => 
        saneArguments.get(param.id).map { v => 
          param.id -> param.convertFromReact(
            preprocess(param, v), 
            preprocess
          )
        }
      }.toMap
    )
  }
}

