package info.vizierdb

import java.io.File

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{ SessionFactory, Session } 
import org.squeryl.adapters.SQLiteAdapter

import info.vizierdb.viztrails.Viztrails
import info.vizierdb.catalog.SharedConnectionSession
import org.squeryl.AbstractSession

object Vizier
{
  var globalSession:Option[AbstractSession] = None
  def catalogTransaction[A](op: => A) = 
    globalSession match {
      case Some(session) => synchronized { using(session)(op) } 
      case None => org.squeryl.PrimitiveTypeMode.transaction[A] { op }
    }

  def initSQLite(db: String) = 
  {
    Class.forName("org.sqlite.JDBC")
    globalSession = Some(Session.create(
        java.sql.DriverManager.getConnection("jdbc:sqlite:" + db),
        new SQLiteAdapter()
    ))
  }
}