package info.vizierdb.catalog

import scalikejdbc._
import java.time.format.DateTimeFormatter
import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.types._
import info.vizierdb.commands.Commands
import info.vizierdb.catalog.binders._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.viztrails.Provenance

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
class Module(
  val id: Identifier,
  val packageId: String,
  val commandId: String,
  val arguments: JsObject,
  val properties: JsObject,
  val revisionOfId: Option[Identifier] = None
)
  extends LazyLogging
{
  override def toString = 
    s"[$id] $packageId.$commandId($arguments)"

  def describe(cell: Cell, projectId: Identifier, branchId: Identifier, workflowId: Identifier, artifacts: Seq[ArtifactRef])(implicit session:DBSession): JsObject = 
  {
    val command = Commands.getOption(packageId, commandId)
    val timestamps:Map[String,String] = Map(
      "createdAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(cell.created)
    ) ++ cell.result.toSeq.flatMap {
      result => Seq(
        "startedAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(result.started)
      ) ++ result.finished.map { finished =>
        "finishedAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(finished)
      }
    }.toMap

    val artifactSummaries = 
                      artifacts
                        .filter { !_.artifactId.isEmpty }
                        .map { ref => 
                          logger.trace(s"Looking up artifact ${ref.userFacingName} -> ${ref.artifactId}")
                          ref.userFacingName -> Artifact.lookupSummary(ref.artifactId.get).get 
                        }

    val datasets    = artifactSummaries.filter { _._2.t.equals(ArtifactType.DATASET) }
    val charts      = artifactSummaries.filter { _._2.t.equals(ArtifactType.CHART) }
    val dataobjects = artifactSummaries.filter { !_._2.t.equals(ArtifactType.DATASET) }

    val messages: Seq[Message] = 
      cell.resultId.map { Result.outputs(_) }.toSeq.flatten


    Json.obj(
      "id" -> id,
      "state" -> ExecutionState.translateToClassicVizier(cell.state),
      "command" -> Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> JsArray(
          arguments.value.map { case (arg, v) => Json.obj("id" -> arg, "value" -> v) }.toSeq
        )
      ),
      "text" -> JsString(command.map { _.format(arguments) }
                                .getOrElse { s"UNKNOWN COMMAND $packageId.$commandId" }),
      "timestamps" -> timestamps,
      "datasets"  -> datasets   .map { case (name, d) => d.summarize(name) },
      "charts"    -> charts     .map { case (name, d) => d.summarize(name) },
      "artifacts" -> dataobjects.map { case (name, d) => d.summarize(name) },
      "outputs" -> Json.obj(
        "stdout" -> messages.filter { _.stream.equals(StreamType.STDOUT) }.map { _.describe },
        "stderr" -> messages.filter { _.stream.equals(StreamType.STDERR) }.map { _.describe }
      ),
      HATEOAS.LINKS -> HATEOAS(
        HATEOAS.SELF           -> VizierAPI.urls.getWorkflowModule(projectId, branchId, workflowId, id),
        HATEOAS.MODULE_INSERT  -> VizierAPI.urls.insertWorkflowModule(projectId, branchId, workflowId, id),
        HATEOAS.MODULE_DELETE  -> VizierAPI.urls.deleteWorkflowModule(projectId, branchId, workflowId, id),
        HATEOAS.MODULE_REPLACE -> VizierAPI.urls.replaceWorkflowModule(projectId, branchId, workflowId, id),
      )
    )

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
    get(
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
      }.updateAndReturnGeneratedKey.apply()
    )
  }


  def get(target: Identifier)(implicit session:DBSession): Module = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Module] = 
    withSQL { 
      val w = Module.syntax 
      select
        .from(Module as w)
        .where.eq(w.id, target) 
    }.map { apply(_) }.single.apply()

  def describeAll(
    projectId: Identifier, 
    branchId: Identifier, 
    workflowId: Identifier,
    cells: Seq[(Cell, Module)]
  )(implicit session: DBSession): JsArray =
  { 
    var scope = Map[String,ArtifactRef]()
    JsArray(
      cells.sortBy { _._1.position }
           .map { case (cell, module) => 
             scope = Provenance.updateRefScope(cell, scope)
             module.describe(
               cell = cell, 
               projectId = projectId,
               branchId = branchId,
               workflowId = workflowId,
               artifacts = scope.values.toSeq
             )
           }
    )
  }


}