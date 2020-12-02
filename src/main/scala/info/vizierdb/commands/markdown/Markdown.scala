package info.vizierdb.commands.markdown

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging

object Markdown extends Command
  with LazyLogging
{
  def name: String = "Markdown Doc"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "markdown", name = "Markdown Code"),
  )
  def format(arguments: Arguments): String = 
    s"MARKDOWN"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    context.message(MIME.MARKDOWN, arguments.get[String]("source"))
  }
}