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
package info.vizierdb.catalog

import scalikejdbc._
import java.time.format.DateTimeFormatter
import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.types._
import info.vizierdb.commands.{ Commands, Parameter }
import info.vizierdb.catalog.binders._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.VizierAPI
import info.vizierdb.viztrails.Provenance
import info.vizierdb.viztrails.ScopeSummary
import info.vizierdb.serialized
import info.vizierdb.commands.markdown.Markdown

/**
 * One step in an arbitrary workflow.
 *
 * A module defines the executable instructions for one step in a workflow.  The position of the
 * module is dictated by the Cell class, defined above.  This allows the same module to be 
 * shared by multiple workflows.
 *
 * A module is guaranteed to be immutable once created and need not be associated with either
 * execution results (since it only describes the configuration of a step), or any workflow (since
 * it may appear in multiple workflows)
 */
case class Module(
  val id: Identifier,
  val packageId: String,
  val commandId: String,
  val arguments: JsObject,
  val properties: JsObject,
  val revisionOfId: Option[Identifier] = None
)
  extends LazyLogging
{
  lazy val command = Commands.getOption(packageId, commandId)
  override def toString = 
    s"[$id] $packageId.$commandId($arguments)"

  def replaceArguments(newArguments: JsObject)(implicit session: DBSession): Module =
  {
    Module.make(
      packageId = packageId,
      commandId = commandId,
      arguments = newArguments,
      properties = properties,
      revisionOfId = Some(id)
    )
  }

  def description = 
    try { 
      command.map { _.format(arguments) }
             .getOrElse { s"UNKNOWN COMMAND $packageId.$commandId" }
    } catch { 
      case e: Exception => 
        s"Error formatting command: [$e]"
    }

  val TOC_HEADER = "(#+) *(.+)".r

  def toc(cell: Cell): Option[serialized.TableOfContentsEntry] =
  {
    (packageId, commandId) match {
      case ("docs", "markdown") => 
      {
        (arguments \ Markdown.PAR_SOURCE).asOpt[String]
          .flatMap { 
            _.split("\n")
             .filter { _ startsWith "#" }
             .collect { 
                case TOC_HEADER(levelPrefix, title) => 
                  (levelPrefix.length, title)
             }
            .headOption
          }
          .map { case (level, title) => 
            serialized.TableOfContentsEntry(
                          title = title, 
                          titleLevel = Some(level),
                          moduleId = id
            )
          }
      }
      case _ => 
        {
          val blurb: String =  
            command.map { _.title(arguments) }
                   .getOrElse { s"${packageId}.${commandId}" }
          Some(
            serialized.TableOfContentsEntry(
              blurb,
              None,
              id
            )
          )
        }
    }
  }

  def argumentList: serialized.CommandArgumentList.T =
    (command match { 
      case None => 
        serialized.CommandArgumentList.toPropertyList(arguments.value.toMap)
      case Some(cmd) => 
        cmd.propertyListFromArguments(arguments)
    }):serialized.CommandArgumentList.T


  /**
   * Generate a description of the module with a specified cell
   * 
   * @param cell        The [[Cell]] to base the description on
   * @param result      The [[Result]] object for the [[Cell]] if one exists
   * @param messages    The [[Messages]] associated with the [[Result]]
   * @param outputs     [[ArtifactSummary]]s for the [[OutputArtifactRef]]s 
   *                    associated with the [[Result]] 
   * @param projectId   The id of the [[Project]] housing the cell's branch
   * @param branchId    The id of the [[Branch]] housing the cell
   * 
   * Virtually all of the above parameters can be retrieved given the cell
   * parameter.  However, doing so requires multiple expensive round trips
   * into the catalog.  As a result, and given the fact that the caller often
   * has these values available, or a more efficient way to look them up, 
   * we're going to add a little friction to the call in the name of efficiency.
   */
  def describe(
    cell: Cell,
    result: Option[Result], 
    messages: Seq[Message],
    outputs: Seq[(String, Artifact)],
    inputs: Seq[(String, Identifier)],
    projectId: Identifier, 
    branchId: Identifier, 
    workflowId: Identifier, 
  )(implicit session: DBSession): () => serialized.ModuleDescription = 
  {

    val timestamps = serialized.Timestamps(
      createdAt = cell.created,
      startedAt = result.map { _.started },
      finishedAt = result.flatMap { _.finished }
    )

    val stdout = messages.filter { _.stream.equals(StreamType.STDOUT) }.map { _.describe }
    val stderr = messages.filter { _.stream.equals(StreamType.STDERR) }.map { _.describe }
    val artifacts = outputs.map { case (name, summ) => summ.summarize(name) }

    {
    () => 
      serialized.ModuleDescription(
        id = cell.moduleDescriptor,
        moduleId = id,
        state = ExecutionState.translateToClassicVizier(cell.state),
        statev2 = cell.state,
        command = serialized.CommandDescription(
          packageId = packageId,
          commandId = commandId,
          arguments = argumentList,
        ),
        text = description,
        toc = toc(cell),
        timestamps = timestamps,

        artifacts = artifacts,
          // artifactSummaries.map { case (name, d) => d.summarize(name) },
        dependencies = inputs.toMap,

        outputs = serialized.ModuleOutputDescription(
          stdout = stdout.map { _() },
          stderr = stderr.map { _() }
        ),
        resultId = cell.resultId,
      )
    }
  } 
}
object Module
  extends SQLSyntaxSupport[Module]
    with LazyLogging
{



  def apply(rs: WrappedResultSet): Module = autoConstruct(rs, (Module.syntax).resultName)
  override def columns = Schema.columns(table)
  def make(
    packageId: String, 
    commandId: String, 
    properties: JsObject = Json.obj(),
    revisionOfId: Option[Identifier] = None,
  )(arguments: (String, Any)*)(implicit session: DBSession): Module =
  {
    val command = Commands.getOption(packageId, commandId)
                  .getOrElse {
                    throw new VizierException(s"Invalid Command ${packageId}.${commandId}")
                  }
    make(packageId, commandId, properties, revisionOfId, command.encodeArguments(arguments.toMap))
  }

  def make(
    packageId: String, 
    commandId: String, 
    arguments: JsObject,
    revisionOfId: Option[Identifier]
  )(implicit session: DBSession): Module =
    make(packageId, commandId, Json.obj(), revisionOfId, arguments)

  def make(
    packageId: String, 
    commandId: String, 
    properties: JsObject,
    revisionOfId: Option[Identifier],
    arguments: JsObject
  )(implicit session: DBSession): Module =
  {    
    val command = 
      Commands.getOption(packageId, commandId)
              .getOrElse { 
                throw new VizierException(s"Invalid Command $packageId.$commandId")
              }
    val argErrors = command.validate(arguments.value.toMap)
    if(!argErrors.isEmpty){
      throw new VizierException(s"Error in command: $packageId.$commandId($arguments)\n${argErrors.mkString("\n")}")
    }
    Module(
      id = 
        withSQL {
          logger.trace(s"Creating Module: ${packageId}.${commandId}(${arguments})")
          val m = Module.column
          insertInto(Module)
            .namedValues(
              m.packageId -> packageId,
              m.commandId -> commandId,
              m.arguments -> arguments,
              m.properties -> properties,
              m.revisionOfId -> revisionOfId
            )
        }.updateAndReturnGeneratedKey.apply(),
      packageId = packageId,
      commandId = commandId,
      arguments = arguments,
      properties = properties,
      revisionOfId = revisionOfId
    )

  }


  def get(target: Identifier)(implicit session:DBSession): Module = getOption(target).get
  def getOption(target: Identifier)(implicit session:DBSession): Option[Module] = 
    withSQL { 
      val w = Module.syntax 
      select
        .from(Module as w)
        .where.eq(w.id, target) 
    }.map { apply(_) }.single.apply()


}

