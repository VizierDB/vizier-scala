/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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

