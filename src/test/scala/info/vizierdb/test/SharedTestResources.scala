package info.vizierdb.test

import java.net.URL
import scalikejdbc.{ GlobalSettings, LoggingSQLAndTimeSettings }
import info.vizierdb.{ Vizier, VizierAPI, VizierURLs, Config }
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
        Vizier.config = Config(Seq())
        if(!Vizier.config.basePath().exists) { Vizier.config.basePath().mkdir() }
        Vizier.initSQLite()
        Vizier.initMimir()
        Schema.drop
        Schema.initialize
        DummyCommands.init

        VizierAPI.urls = 
          new VizierURLs(
            new URL(s"http://localhost:5000/"), 
            new URL(s"http://localhost:5000/vizier-db/api/v1/"), 
            None
          )
      }
      sharedSetupComplete = true
    }
  }
}