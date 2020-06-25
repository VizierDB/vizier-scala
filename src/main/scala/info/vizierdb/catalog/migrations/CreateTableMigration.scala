package info.vizierdb.catalog.migrations

import scalikejdbc._
import scalikejdbc.metadata._

case class CreateTableMigration(
  table: Table
) extends Migration
{
  val primaryKeys = table.columns.filter { _.isPrimaryKey }

  def apply(implicit session: DBSession) = 
    session.executeUpdate(sql)
  def drop(implicit session: DBSession) = 
    session.executeUpdate(s"DROP TABLE ${table.name}") 



  def sql(column: Column): String = 
  {
    Seq(
      Some(column.name),
      Some(column.typeName),
      if(column.isRequired) { Some("NOT NULL") } else { None },
      if(column.isPrimaryKey && primaryKeys.size == 1) { Some("PRIMARY KEY") } else { None },
      if(column.isAutoIncrement) { Some("AUTOINCREMENT") } else { None },
      Option(column.defaultValue).map { "DEFAULT VALUE "+_  }
    ).flatten.mkString(" ")
  }

  def sql: String = 
  {
    val elements = Seq[Iterable[String]](
      table.columns.map { sql(_) },
      if(primaryKeys.size > 1){ Some("PRIMARY KEY("+primaryKeys.map { _.name }.mkString(", ")+")") }
                         else { None }
    ).flatten
    s"""
    CREATE TABLE ${table.name}(
      ${elements.mkString(",\n      ")}
    );
    """
  }

  def updateSchema(sch: Map[String, Table]): Map[String, Table] = 
  {
    assert(!(sch contains table.name))
    sch ++ Map(table.name.toLowerCase() -> table)
  }
}
