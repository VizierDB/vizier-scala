/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.catalog.migrations

import scalikejdbc.DBSession
import scalikejdbc.metadata.{ Table, Column }

case class AddColumnMigration(
  table: String,
  column: Column
) extends Migration
{
  def apply(implicit session: DBSession): Unit =
    session.executeUpdate(sql)
  def drop(implicit session: DBSession): Unit =
    session.executeUpdate(s"ALTER TABLE `${table}` DROP COLUMN `${column.name}`")

  def updateSchema(sch: Map[String, Table]): Map[String, Table] =
  {
    val tableDefn = sch(table.toLowerCase())
    return sch ++ Map(
      table -> tableDefn.copy(
        columns = tableDefn.columns :+ column
      )
    )
  }

  def sql: String =
    s"ALTER TABLE ${table.toLowerCase()} ADD COLUMN ${Migration.columnSql(column)}"
}

