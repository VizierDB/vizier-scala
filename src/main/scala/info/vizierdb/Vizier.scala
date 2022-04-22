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
import info.vizierdb.catalog.Doctor
import info.vizierdb.commands.python.SparkPythonUDFRelay
import org.apache.spark.UDTRegistrationProxy
import java.awt.image.BufferedImage
import org.apache.spark.sql.types.ImageUDT
import scala.sys.process.Process
import org.mimirdb.caveats.Caveat
import info.vizierdb.util.StringUtils

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

        // If you are here to up the connection time-out period because you're getting connection
        // timeouts, read this first please:
        //
        // https://github.com/VizierDB/vizier-scala/wiki/DevGuide-Gotchas#scalikejdbc
        //
        // TL;DR: You are almost certainly creating a nested session via DB.readOnly or 
        //        DB.autocommit  Look through the stack trace.  These methods are NOT reentrant,
        //        will trigger a connection timeout error when you're using SQLite, and will lead
        //        to weird inconsistent state when you're using a database that allows parallel
        //        connections.
        /* ^^^^^ */ connectionTimeoutMillis = 5000l  /* ^^^^^ */
        // Read the above comment before modifying connectionTimeoutMillis please.
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
    UDTRegistrationProxy.register(classOf[BufferedImage].getName, classOf[ImageUDT].getName)
    MimirAPI.blobs = info.vizierdb.commands.python.SparkPythonUDFRelay
    MimirAPI.pythonUDF = info.vizierdb.commands.python.PythonProcess.udfBuilder
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

  def launchUIIfPossible()
  {
    val command: Seq[String] = 
      System.getProperty("os.name").toLowerCase match {
        case "linux"  => Seq("xdg-open", VizierAPI.urls.ui.toString)
        case "darwin" | "mac os x" => Seq("open", VizierAPI.urls.ui.toString)
        case _ => { println(s"please open ${VizierAPI.urls.ui.toString} in your browser to start using Vizier (you are runnning Vizier on [${System.getProperty("os.name").toLowerCase}])") 
          return
        }
      }
    try {
      println("Opening Vizier in your brower...")
      Process(command).!
    }
    catch {
      case e: Exception => println(s"... failed to open browers using <$command>. Don't worry, just open ${VizierAPI.urls.ui.toString} manually in your browser.")
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
    if(config.workingDirectory.isDefined){
      System.setProperty("user.dir", config.workingDirectory())
    }

    // Set up Mimir
    println("Starting Mimir...")
    initMimir(
      runServer = !config.commandOrSubcommandNeedsMimir
    )

    config.subcommand match {
      //////////////// HANDLE SPECIAL COMMANDS //////////////////
      case Some(subcommand) => 
        
        //////////////////// Ingest ////////////////////
        if(subcommand.equals(config.ingest)){
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
        //////////////////// Export ////////////////////
        } else if (subcommand.equals(config.export)){
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
        //////////////////// Doctor ////////////////////
        } else if (subcommand.equals(config.doctor)) {
          println("Checking project database...")
          val errors = Doctor.checkup()
          for(msg <- errors){
            println(msg)
          }
          if(errors.isEmpty){ 
            println("No problems found")
          }

        //////////////////// Run ////////////////////
        } else if(subcommand.equals(config.run)) {
          VizierAPI.init()
          val project = 
            MutableProject.find(config.run.project())
                          .getOrElse { 
                            println(s"Did not find project: '${config.run.project()}'")
                            System.exit(-1)
                            null
                          }
          config.run.branch.foreach { branchNameOrId => 
            val branch = project.findBranch(branchNameOrId)
                                .getOrElse {
                                  println(s"Did not find branch '$branchNameOrId' in project ${project.project.name}")
                                  System.exit(-1)
                                  null
                                }
            project.workWithBranch(branch.id)
          }
          println(s"Running ${project.project.name}, Branch ${project.branch.name}")
          if(config.run.cells().isEmpty){
            println("... clearing all cell results")
            project.invalidateAllCells
          } else {
            println(s"... clearing results for cells: ${StringUtils.oxfordComma(config.run.cells().map { _.toString })}")
            project.invalidate( config.run.cells() )
          }
          println("Waiting for execution to finish...")
          try {
            project.waitUntilReadyAndThrowOnError
          } catch {
            case e: Throwable =>
              println(s"Problem During Re-execution: ${e.getMessage}")
              System.exit(-1)
          }
          println("... execution finished.")
          if(config.run.showCaveats()){
            val caveats: Map[String, Seq[Caveat]] =
              project.artifactSummaries
                     .toSeq
                     .filter { _._2.t.equals(ArtifactType.DATASET) }
                     .map { case (name, artifact) =>
                        name -> org.mimirdb.api.request.Explain(
                          s"SELECT * FROM ${artifact.nameInBackend}",
                        )
                     }
                     .filterNot { _._2.isEmpty }
                     .toMap
            if(!caveats.isEmpty){
              System.err.println("\nThere were potential problems with generated datasets")
              for( (artifact, caveats) <- caveats ){
                System.err.println(s"\n==== $artifact ====")
                for( caveat <- caveats ){
                  System.err.println( " * " + caveat.message )
                }
              }
              System.err.println("")
              System.exit(-1)
            } else {
              println("... all datasets check out")
              System.exit(0)
            }
          }

        } else { 
          println(s"Unimplemented subcommand $subcommand")
          System.exit(-1)
        }

      //////////////// SPIN UP THE SERVER //////////////////
      case None => 
        println("Starting server...")
        VizierAPI.init()
        println(s"... server running at < ${VizierAPI.urls.ui} >")

        // Don't auto-launch the UI if we're asked not to
        // or if we're in server mode.
        if(!config.noUI() && !config.serverMode()){ launchUIIfPossible() }

        VizierAPI.server.join()
    }
  }
}

