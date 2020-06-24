package info.vizierdb.commands

import play.api.libs.json.{ JsObject, JsValue }
import info.vizierdb.VizierException

trait Command
{
  def name: String
  def parameters: Seq[Parameter]
  def format(arguments: JsObject): String = format(Arguments(arguments.as[Map[String, JsValue]], parameters))
  def format(arguments: Arguments): String
  def process(arguments: Arguments, context: ExecutionContext): Unit
  def encodeArguments(arguments: Map[String, Any]): JsObject =
    JsObject(parameters.map { parameter => 
      val encoded:JsValue =
        arguments.get(parameter.id)
                 .map { v => parameter.encode(v) }
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

}