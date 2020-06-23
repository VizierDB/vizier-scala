package info.vizierdb.test

import org.squeryl.PrimitiveTypeMode._
import info.vizierdb.viztrails.Viztrails
import info.vizierdb.Vizier

object SharedTestResources
{
  var sharedSetupComplete: Boolean = false

  def init()
  {
    synchronized { 
      if(!sharedSetupComplete) {
        Vizier.initSQLite("target/Test.db")
        Vizier.catalogTransaction { 
          // Try to drop the table before to clean up.  
          // ignore errors... just means there's no tables yet
          // (better than doing it after, since this deals with crashes and allows us to
          // introspect the SQLite DB afterwards)
          try { Viztrails.drop } catch { case _: Throwable  => () } 
          Viztrails.create 
        }
        DummyCommands.init
      }
      sharedSetupComplete = true
    }
  }
}