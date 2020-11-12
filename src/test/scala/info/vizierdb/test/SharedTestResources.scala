package info.vizierdb.test

import scalikejdbc.{ GlobalSettings, LoggingSQLAndTimeSettings }
import info.vizierdb.Vizier
import info.vizierdb.catalog.Schema

object SharedTestResources
{
  var sharedSetupComplete: Boolean = false

  def init()
  {
    synchronized { 
      if(!sharedSetupComplete) {

        GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
          enabled = true,
          singleLineMode = true,
          logLevel = 'trace,
        ) 

        Vizier.initSQLite()
        Vizier.initMimir()
        Schema.drop
        Schema.initialize
        DummyCommands.init
      }
      sharedSetupComplete = true
    }
  }
}