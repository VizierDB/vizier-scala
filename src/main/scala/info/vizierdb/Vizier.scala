package info.vizierdb

import scalikejdbc._

import java.sql.DriverManager
import info.vizierdb.types._
import info.vizierdb.catalog.workarounds.SQLiteNoReadOnlyDriver

object Vizier
{

  def initSQLite(db: String) = 
  {
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    ConnectionPool.singleton(
      url = "no-read-only:jdbc:sqlite:" + db,
      user = "",
      password = ""
    )
  }
  
}