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
package info.vizierdb.test

import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands._
import info.vizierdb.catalog.Artifact
import info.vizierdb.types.ArtifactType


object DummyCommands 
{
  def init =
  {
    Commands.register("dummy", "Testing Commands", "dummy")(
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
  def title(arguments: Arguments): String = 
    format(arguments)
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val v = arguments.get[String]("value")
    logger.debug(s"Printing: $v")
    context.message(v)
  }
  def predictProvenance(arguments: Arguments) = None
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
  def title(arguments: Arguments): String = 
    format(arguments)
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val dataset = arguments.get[String]("dataset") 
    val artifact = context.output(dataset, ArtifactType.BLOB, arguments.get[String]("content").getBytes())
    logger.debug(s"Creating: $dataset -> ${artifact.id}")
    context.message(s"Created artifact $dataset -> ${artifact.id} ")
  }
  def predictProvenance(arguments: Arguments) = None
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
  def title(arguments: Arguments): String = 
    format(arguments)
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasets = arguments.get[Seq[Map[String, JsValue]]]("datasets")
                            .map { _("dataset").as[String] }
    logger.debug(s"Consuming Datasets ${datasets.mkString(", ")} with context $context")
    val results = 
      datasets.map { context.artifact(_).get.string }  
    context.message(results.mkString)
  }
  def predictProvenance(arguments: Arguments) = None
}

