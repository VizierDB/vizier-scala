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
import info.vizierdb.serialized
import info.vizierdb.viztrails.ProvenancePrediction

trait Command
{

  /**
   * The name of this command as displayed in the UI
   */
  def name: String

  /**
   * A list of parameters for this command 
   * 
   * @return                 A list of [[Parameter]]s
   * 
   * The parameter list is both used for both parsing arguments from the client,
   * as well as determining which arguments are displayed in the UI.
   */
  def parameters: Seq[Parameter]

  /**
   * Generate a concise, multi-line summary of this command
   * 
   * @param   arguments      The encoded argument object
   * @return                 A string summary based on the arguments
   */
  def format(arguments: JsObject): String = format(Arguments(arguments.as[Map[String, JsValue]], parameters))

  /**
   * Generate a concise, multi-line summary of this command
   * 
   * @param   arguments      The decoded argument object
   * @return                 A string summary based on the arguments
   */
  def format(arguments: Arguments): String

  /**
   * Generate a very short 1-4 word title for an instance of this command
   * 
   * @param   arguments      The decoded argument object
   * @return                 A 1-4 word title for use in the table of context
   */
  def title(arguments: JsObject): String = title(Arguments(arguments.as[Map[String, JsValue]], parameters))

  /**
   * Generate a very short 1-4 word title for an instance of this command
   * 
   * @param   arguments      The decoded argument object
   * @return                 A 1-4 word title for use in the table of context
   */
  def title(arguments: Arguments): String

  /**
   * Process the command on a given input arguments.
   * 
   * @param   arguments      The decoded argument object
   * @param   context        The execution context in which to evaluate the command
   *
   * This method implements the core logic of the command.  The execution context
   * provides the system state before the command is invoked, and can be used to
   * register output artifacts and/or messages to the console.
   */
  def process(arguments: Arguments, context: ExecutionContext): Unit

  /**
   * Return true if the command should be hidden from users
   */
  def hidden: Boolean = false

  /**
   * Validate a Json object that claims to be arguments to this command
   * 
   * @param   arguments       The map corresponding to the Json arguments object
   * 
   * @return                  An empty sequence if the command is valid or a sequence
   *                          of error messages, each describing a problem.
   */
  def validate(arguments: Map[String, JsValue]): Seq[String] = 
    parameters.flatMap { parameter => 
      arguments.get(parameter.id) match {
        case Some(argument) => parameter.validate(argument)
        case None if parameter.required && parameter.getDefault.equals(JsNull) 
                            => Seq(s"Missing argument ${parameter.id} // ${parameter.getDefault}")
        case None => Seq()
      }
    }

  /**
   * Encode a Json arguments object into a Json encoding object (for testing/scala use)
   *
   * @param   arguments       A map of argument name -> argument value pairs as scala values
   * @param   base            Default parameters for this command
   * @return                  Json encoded arguments for this command
   * 
   * This method is largely for use with [[MutableProject]], providing an easy way to
   * (re-)encode arguments coming in from scala land.  
   */ 
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

  /**
   * Decode a react arguments table into a "native" Json encoding of the arguments
   *
   * @param   arguments       A Json value in react table format
   * @param   preprocess      A preprocessing step to transform Json values encoded this way
   * 
   * React (and legacy versions of Vizier) use a slightly more cumbersome encoding of 
   * argument tables than are used here.  
   * <pre>
   * [ 
   *   { "id" : "argument1", value: ... },
   *   { "id" : "argument2", value: ... },
   *   ...
   * ]
   * </pre>
   * This method translates the react-encoded Json arguments table into a native
   * encoding:
   * <pre>
   * { "argument1" : ...,
   *   "argument2" : ...,
   *   ...
   * }
   * </pre>
   */
  def argumentsFromPropertyList(
    arguments: serialized.CommandArgumentList.T, 
    preprocess: ((Parameter, JsValue) => JsValue) = { (_, x) => x } 
  ): JsObject =
  {
    val saneArguments = 
      serialized.CommandArgumentList.toMap(arguments)

    JsObject(
      parameters.flatMap { param => 
        saneArguments.get(param.id).map { v => 
          param.id -> param.convertFromProperty(
            preprocess(param, v), 
            preprocess
          )
        }
      }.toMap
    )
  }
  /**
   * Translate a native arguments object into a react arguments table
   * 
   * @param   arguments      The encoded argument object
   * @return                 The arguments object encoded in react format
   * 
   * See [[decodeReactArguments]]; This is the inverse
   */
  def propertyListFromArguments(
    arguments: JsObject
  ): serialized.CommandArgumentList.T = 
    parameters.flatMap { param => 
      arguments.value.get(param.id).map { j => 
        serialized.CommandArgument(id = param.id, value = param.convertToProperty(j))
      }
    }

  /**
   * Apply a mapping to replace a specific parameter in the argument list
   * @param  arguments  The arguments to replace
   * @param  rule       A partial function from ([[Parameter]], [[JsObject]]) to the new value, 
   *                    or None if no replacement is needed
   * @return            Some(the new arguments), or None if no replacements were made
   */
  def replaceArguments(arguments: JsObject)(rule: PartialFunction[(Parameter, JsValue),JsValue]): Option[JsValue] =
  {
    val base = arguments.as[Map[String, JsValue]]
    val lineReplacements = 
      parameters.map { param =>
        param.id -> 
          param.replaceParameterValue(base.getOrElse(param.id, JsNull), rule)
      }.filter { _._2.isDefined }
       .map { x => x._1 -> x._2.get }
       .toMap
    if(lineReplacements.isEmpty) { None }
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

  /**
   * Predict the provenance (inputs and outputs) of this command from arguments
   * 
   * @param   arguments      The encoded argument object
   * @return                 Optionally a [[ProvenancePrediction]] object
   * 
   * Some commands operate on inputs/outputs that can be statically inferred from the
   * arguments.  Although not presently used anywhere, this information will be used to
   * allow concurrent execution of cells in the future
   */
  def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction

  /**
   * Predict the provenance (inputs and outputs) of this command from arguments
   * 
   * @param   arguments      The Json encoded argument object
   * @return                 Optionally a [[ProvenancePrediction]] object
   * 
   * Some commands operate on inputs/outputs that can be statically inferred from the
   * arguments.  Although not presently used anywhere, this information will be used to
   * allow concurrent execution of cells in the future
   */
  def predictProvenance(arguments: JsObject, properties: JsObject): ProvenancePrediction =
    predictProvenance(Arguments(arguments.as[Map[String, JsValue]], parameters), properties)

}

