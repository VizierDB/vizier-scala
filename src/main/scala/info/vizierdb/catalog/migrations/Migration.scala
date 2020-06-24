package info.vizierdb.catalog.migrations

import scalikejdbc.DBSession

trait Migration
{
  def apply(implicit session: DBSession): Unit

  def drop(implicit session: DBSession): Unit

  def sql: String
}