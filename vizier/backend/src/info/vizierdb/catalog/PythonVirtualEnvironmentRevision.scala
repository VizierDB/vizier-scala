package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.serialized.PythonPackage
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.binders._

case class PythonVirtualEnvironmentRevision(
  id: Identifier,
  packages: Seq[PythonPackage]
) extends LazyLogging

object PythonVirtualEnvironmentRevision 
  extends SQLSyntaxSupport[PythonVirtualEnvironmentRevision]
{
  def apply(rs: WrappedResultSet): PythonVirtualEnvironmentRevision = 
    autoConstruct(rs, (PythonVirtualEnvironmentRevision.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(id: Identifier)(implicit session: DBSession): PythonVirtualEnvironmentRevision =
    getOption(id).get

  def getOption(id: Identifier)(implicit session: DBSession): Option[PythonVirtualEnvironmentRevision] =
    withSQL { 
      val b = PythonVirtualEnvironmentRevision.syntax 
      select
        .from(PythonVirtualEnvironmentRevision as b)
        .where.eq(b.id, id)
    }.map { apply(_) }.single.apply()

    
}
