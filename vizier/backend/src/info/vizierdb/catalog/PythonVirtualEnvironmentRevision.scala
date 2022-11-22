package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.serialized.PythonPackage
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.binders._

case class PythonVirtualEnvironmentRevision(
  revisionId: Identifier,
  envId: Identifier,
  packages: Seq[PythonPackage]
) extends LazyLogging
{
  def init(env: PythonVirtualEnvironment){
    packages.foreach { pkg => 
      logger.info(s"Installing into venv ${env.name}: $pkg")
      env.Environment.install(pkg.name, pkg.version) 
    }
  }

}
object PythonVirtualEnvironmentRevision 
  extends SQLSyntaxSupport[PythonVirtualEnvironmentRevision]
{
  def apply(rs: WrappedResultSet): PythonVirtualEnvironmentRevision = 
    autoConstruct(rs, (PythonVirtualEnvironmentRevision.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(envId: Identifier, revisionId: Identifier)(implicit session: DBSession): PythonVirtualEnvironmentRevision =
    getOption(envId, revisionId).get

  def getOption(envId: Identifier, revisionId: Identifier)(implicit session: DBSession): Option[PythonVirtualEnvironmentRevision] =
    withSQL { 
      val b = PythonVirtualEnvironmentRevision.syntax 
      select
        .from(PythonVirtualEnvironmentRevision as b)
        .where.eq(b.envId, envId)
          .and.eq(b.revisionId, revisionId)
    }.map { apply(_) }.single.apply()

  def getActive(envId: Identifier)(implicit session: DBSession): PythonVirtualEnvironmentRevision =
    getActiveOption(envId).get

  def getActiveOption(envId: Identifier)(implicit session: DBSession): Option[PythonVirtualEnvironmentRevision] =
    withSQL { 
      val a = PythonVirtualEnvironment.syntax 
      val b = PythonVirtualEnvironmentRevision.syntax 
      select
        .from(PythonVirtualEnvironmentRevision as b)
        .innerJoin(PythonVirtualEnvironment as a)
        .where.eq(b.envId, envId)
          .and.eq(a.id, envId)
          .and.eq(b.revisionId, a.activeRevision)
    }.map { apply(_) }.single.apply()


}
