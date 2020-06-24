package info.vizierdb.catalog

import scalikejdbc._
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
object Module
  extends SQLSyntaxSupport[Module]
{
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

  def apply(rs: WrappedResultSet): Module = autoConstruct(rs, (Module.syntax).resultName)

  def get(target: Identifier)(implicit session:DBSession): Module = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Module] = 
    withSQL { 
      val w = Module.syntax 
      select
        .from(Module as w)
        .where.eq(w.id, target) 
    }.map { apply(_) }.single.apply()

}