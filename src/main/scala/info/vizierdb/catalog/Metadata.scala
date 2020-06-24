package info.vizierdb.catalog

import scalikejdbc._

case class Metadata(key: String, value: String)
object Metadata extends SQLSyntaxSupport[Metadata]
{
  def apply(p: SyntaxProvider[Metadata])(rs: WrappedResultSet): Metadata = 
    apply(p.resultName)(rs)
  def apply(p: ResultName[Metadata])(rs: WrappedResultSet): Metadata = 
    new Metadata(rs.string(p.key), rs.string(p.value))

  def lookup(key: String)(implicit session: DBSession): Option[String] =
    sql"SELECT value FROM Metadata WHERE key = $key"
      .map { _.string(1) }
      .single()
      .apply()

  def get(key: String)(implicit session: DBSession): String = lookup(key).get

  def put(key: String, value: String)(implicit session: DBSession) = 
    sql"INSERT OR REPLACE INTO Metadata(key, value) VALUES($key, $value)".execute.apply()
}