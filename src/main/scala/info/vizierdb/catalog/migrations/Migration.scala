package info.vizierdb.catalog.migrations

import scalikejdbc.DBSession
import scalikejdbc.metadata.Table

trait Migration
{
  def apply(implicit session: DBSession): Unit
  def drop(implicit session: DBSession): Unit
  def updateSchema(sch: Map[String, Table]): Map[String, Table]

  def sql: String

}