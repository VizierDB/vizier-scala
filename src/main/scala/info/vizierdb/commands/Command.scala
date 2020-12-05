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
        case None if parameter.required => Seq(s"Missing argument $parameter.id")
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
  def decodeReactArguments(arguments: JsValue): JsObject =
  {
    val saneArguments = 
      arguments.as[Seq[Map[String,JsValue]]]
               .map { arg => 
                 arg("id").as[String] -> arg("value")
               }
               .toMap

    JsObject(
      parameters.flatMap { param => 
        saneArguments.get(param.id).map { v => 
          param.id -> param.convertFromReact(v)
        }
      }.toMap
    )
  }
}