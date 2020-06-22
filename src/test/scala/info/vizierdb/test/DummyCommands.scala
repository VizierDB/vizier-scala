package info.vizierdb.test

import org.squeryl.PrimitiveTypeMode._
import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands._
import info.vizierdb.viztrails.{ ArtifactType, Artifact }


object DummyCommands 
{
  def init
  {
    Commands.register("dummy")(
      "print" -> DummyPrint,
      "create" -> DummyCreate,
      "consume" -> DummyConsume
    )
  }

}

object DummyPrint extends Command with LazyLogging
{
  def name: String = "Dummy Print Command"
  def parameters: Seq[Parameter] = Seq(
    StringParameter("value", "Thing to Print")
  )
  def format(arguments: Arguments): String = 
    s"PRINT ${arguments.pretty("value")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val v = arguments.get[String]("value")
    logger.debug(s"Printing: $v")
    context.logEntry(v)
  }
}

object DummyCreate extends Command with LazyLogging
{
  def name: String = "Dummy Create Artifact"
  def parameters: Seq[Parameter] = Seq(
    StringParameter("dataset", "Artifact Name"),
    StringParameter("content", "Artifact Content", default = Some("NO SOUP FOR YOU"))
  )
  def format(arguments: Arguments): String = 
    s"CREATE DUMMY ${arguments.pretty("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val dataset = arguments.get[String]("dataset")
    transaction {
      val artifact = Artifact.make(ArtifactType.BLOB, arguments.get[String]("content").getBytes())
      context.output(dataset, artifact)
      logger.debug(s"Creating: $dataset -> ${artifact.id}")
      context.logEntry(s"Created artifact $dataset -> ${artifact.id} ")
    }
  }
}

object DummyConsume extends Command with LazyLogging
{
  def name: String = "Dummy Consume Artifacts"
  def parameters: Seq[Parameter] = Seq(
    ListParameter("datasets", "Artifacts", components = Seq(
      StringParameter("dataset", "Name")
    ))
  )
  def format(arguments: Arguments): String = 
    s"CONSUME DUMMY ${arguments.pretty("datasets")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasets = arguments.get[Seq[Map[String, JsValue]]]("datasets")
                            .map { _("dataset").as[String] }
    logger.debug(s"Consuming Datasets ${datasets.mkString(", ")} with context $context")
    transaction {
      val results = 
        datasets.map { context.artifact(_).get.string } 
      context.logEntry(results.mkString)
    }
  }
}