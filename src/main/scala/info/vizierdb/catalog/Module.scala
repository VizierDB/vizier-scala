package info.vizierdb.catalog

import scalikejdbc._
import java.time.format.DateTimeFormatter
import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.types._
import info.vizierdb.commands.Commands
import info.vizierdb.catalog.binders._

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
{
  def describe(cell: Cell, projectId: Identifier, branchId: Identifier, workflowId: Identifier)(implicit session:DBSession): JsObject = 
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

    val artifacts = cell.resultId.map { Result.outputArtifacts(_) }
                        .toSeq.flatten
                        .filter { !_.artifactId.isEmpty }
                        .map { ref => ref.userFacingName -> Artifact.lookupSummary(ref.artifactId.get).get }

    val datasets    = artifacts.filter { _._2.t.equals(ArtifactType.DATASET) }
    val charts      = artifacts.filter { _._2.t.equals(ArtifactType.CHART) }
    val dataobjects = artifacts.filter { !_._2.t.equals(ArtifactType.DATASET) }

    val messages: Seq[Message] = 
      cell.resultId.map { Result.outputs(_) }.toSeq.flatten


    Json.obj(
      "id" -> id,
      "state" -> cell.state.toString,
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
      "datasets"  -> datasets   .map { _._2.describe },
      "charts"    -> charts     .map { _._2.describe },
      "artifacts" -> dataobjects.map { _._2.describe },
      "outputs" -> Json.obj(
        "stdout" -> messages.filter { _.stream.equals(StreamType.STDOUT) }.map { _.describe },
        "stderr" -> messages.filter { _.stream.equals(StreamType.STDERR) }.map { _.describe }
      )
    )

  } 
}
object Module
  extends SQLSyntaxSupport[Module]
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
    
    get( withSQL {
      val m = Module.column
      insertInto(Module)
        .namedValues(
          m.packageId -> packageId,
          m.commandId -> commandId,
          m.arguments -> command.encodeArguments(arguments.toMap),
          m.properties -> properties,
          m.revisionOfId -> revisionOfId
        )
    }.updateAndReturnGeneratedKey.apply())
  }


  def get(target: Identifier)(implicit session:DBSession): Module = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Module] = 
    withSQL { 
      val w = Module.syntax 
      select
        .from(Module as w)
        .where.eq(w.id, target) 
    }.map { apply(_) }.single.apply()

}