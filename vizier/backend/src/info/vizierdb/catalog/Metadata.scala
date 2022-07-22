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

case class Metadata(key: String, value: String)
object Metadata extends SQLSyntaxSupport[Metadata]
{
  def apply(p: SyntaxProvider[Metadata])(rs: WrappedResultSet): Metadata = 
    apply(p.resultName)(rs)
  def apply(p: ResultName[Metadata])(rs: WrappedResultSet): Metadata = 
    new Metadata(rs.string(p.key), rs.string(p.value))

  def getOption(key: String)(implicit session: DBSession): Option[String] =
    sql"SELECT value FROM Metadata WHERE key = $key"
      .map { _.string(1) }
      .single()

  def get(key: String)(implicit session: DBSession): String = getOption(key).get

  def put(key: String, value: String)(implicit session: DBSession) = 
    sql"INSERT OR REPLACE INTO Metadata(key, value) VALUES($key, $value)".execute.apply()

  def all(implicit session: DBSession): Map[String, String] = 
    sql"SELECT key, value FROM Metadata"
      .map { result => result.string(0) -> result.string(1) }
      .list()
      .toMap
}

