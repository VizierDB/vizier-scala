/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
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
package info.vizierdb

import scalikejdbc._
import java.sql.DriverManager

import org.mimirdb.api.{ MimirAPI, InitSpark, MimirConfig }
import org.mimirdb.data.{ JDBCMetadataBackend => MimirJDBC, Catalog => MimirCatalog }

import info.vizierdb.types._
import info.vizierdb.catalog.workarounds.SQLiteNoReadOnlyDriver
import info.vizierdb.catalog.{ Project, Schema, Cell }
import org.mimirdb.data.LocalFSStagingProvider
import java.io._
import java.util.Properties
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.export.{ ExportProject, ImportProject }
import info.vizierdb.util.Streams
import org.mimirdb.util.ExperimentalOptions
import info.vizierdb.commands.python.PythonProcess
import py4j.reflection.PythonProxyHandler

object Vizier
  extends LazyLogging
{
  var config: Config = null

  def initSQLite(db: String = "Vizier.db") = 
  {
    // Instead of using the default SQLite driver, we're going to use the following workaround.
    // Specifically, The SQLite driver doesn't like it when you change the READ-ONLY status of a 
    // connection once it's open, while also not liking > 1 connection open and committing at the
    // same time.  Instead, we're going to simply disable READ-ONLY mode.  This is going to be
    // slightly slower, 
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    ConnectionPool.singleton(
      url = "no-read-only:jdbc:sqlite:" + new File(config.basePath(), db).toString,
      user = "",
      password = "",
      settings = ConnectionPoolSettings(
        initialSize = 1,
        maxSize = 1,
        connectionTimeoutMillis = 30000l
      )
    )
  }

  def initMimir(
    db: String = "Mimir.db", 
    stagingDirectory: String = "staging", 
    runServer: Boolean = true
  ) =
  {
    config.setMimirConfig
    MimirAPI.sparkSession = InitSpark.local
    InitSpark.initPlugins(MimirAPI.sparkSession)
    MimirAPI.metadata = new MimirJDBC("sqlite", new File(config.basePath(), db).toString)
    MimirAPI.catalog = new MimirCatalog(
      MimirAPI.metadata,
      new LocalFSStagingProvider(
        basePath = stagingDirectory, 
        basePathIsRelativeToDataDir = true
      ),
      MimirAPI.sparkSession
    )
    val geocoders = 
      Seq(
        config.googleAPIKey.map { k =>
          logger.debug("Google Services Will Be Available")
          new org.mimirdb.lenses.implementation.GoogleGeocoder(k) 
        }.toOption,
        config.osmServer.map { k =>
          logger.debug("OSM Services Will Be Available")
          new org.mimirdb.lenses.implementation.OSMGeocoder(k) 
        }.toOption
      ).flatten
    if(!geocoders.isEmpty){ 
      org.mimirdb.lenses.Lenses.initGeocoding(geocoders, MimirAPI.catalog) 
    }
    if(runServer){
      MimirAPI.runServer(MimirAPI.DEFAULT_API_PORT) // Starts the Mimir server **in the background**
    }
  }

  def initORMLogging()
  {
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = true,
      logLevel = 'trace,
    ) 
  }

  def bringDatabaseToSaneState()
  {
    DB.autoCommit { implicit s => 
      Cell.abortEverything()
    }
  }

  def main(args: Array[String]) 
  {
    config = new Config(args)

    // Enable relevant experimental options
    ExperimentalOptions.enable(config.experimental())

    // Handle the case where we were asked to print a help banner
    if(config.help()){
      config.printHelp()
      return
    }

    // Override the default python version (or automatically pick one)
    if(config.pythonPath.isSupplied){
      PythonProcess.PYTHON_COMMAND = config.pythonPath()
    } else {
      PythonProcess.discoverPython()
    }

    // Check for non-mandatory dependencies
    println("Checking for dependencies...")
    PythonProcess.checkPython()

    // Set up the Vizier directory and database
    println("Setting up project library...")
    if(!config.basePath().exists) { config.basePath().mkdir() }
    initSQLite()
    Schema.initialize()
    initORMLogging()
    bringDatabaseToSaneState()

    // Set up Mimir
    println("Starting Mimir...")
    initMimir(
      runServer = 
        !config.ingest.file.isSupplied
        && !config.export.projectId.isSupplied
    )

    //////////////// HANDLE SPECIAL COMMANDS //////////////////
    // Ingest
    if(config.ingest.file.isSupplied){
      try {
        Streams.closeAfter(new FileInputStream(config.ingest.file())) { 
          ImportProject(
            _,
            execute = config.ingest.execute()
          )
        }
      } catch {
        case e:VizierException => 
          println(s"\nError: ${e.getMessage()}")
      }

    // Export
    } else if (config.export.projectId.isSupplied){
      try { 
        Streams.closeAfter(new FileOutputStream(config.export.file())) { 
          ExportProject(
            config.export.projectId(),
            _
          )
        }
        println(s"\nExported project ${config.export.projectId()} to '${config.export.file()}'")
      } catch {
        case e:VizierException => 
          println(s"\nError: ${e.getMessage()}")
      }

    //////////////// SPIN UP THE SERVER //////////////////
    } else {
      println("Starting server...")
      VizierAPI.init()
      println(s"... Server running at < ${VizierAPI.urls.ui} >")
      VizierAPI.server.join()
    }
  }
}

