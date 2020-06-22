package info.vizierdb

import java.io.File

import org.squeryl.{ SessionFactory, Session } 
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.adapters.SQLiteAdapter

import info.vizierdb.viztrails.Viztrails

object Vizier
{
  def initSQLite(db: String) = 
  {
    Class.forName("org.sqlite.JDBC")
    SessionFactory.concreteFactory = Some(() => {
      Session.create(
        java.sql.DriverManager.getConnection("jdbc:sqlite:" + db),
        new SQLiteAdapter()
      )
    })
  }
}