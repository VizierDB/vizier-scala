package info.vizierdb.test

import info.vizierdb.Vizier
import info.vizierdb.catalog.Schema

object SharedTestResources
{
  var sharedSetupComplete: Boolean = false

  def init()
  {
    synchronized { 
      if(!sharedSetupComplete) {
        Vizier.initSQLite("target/Test.db")
        Schema.drop
        Schema.initialize
        DummyCommands.init
      }
      sharedSetupComplete = true
    }
  }
}