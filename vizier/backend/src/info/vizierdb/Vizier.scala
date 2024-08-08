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
import java.io._
import java.net.URL
import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession

import info.vizierdb.catalog.{
  Doctor,
  Project, 
  Schema, 
  Cell,
}
import info.vizierdb.catalog.workarounds.SQLiteNoReadOnlyDriver
import info.vizierdb.commands.python.PythonProcess
import info.vizierdb.export.{ 
  ExportProject, 
  ImportProject,
}
import info.vizierdb.spark.InitSpark
import info.vizierdb.types._
import info.vizierdb.util.{
  ExperimentalOptions,
  Streams,
}
import py4j.reflection.PythonProxyHandler
import info.vizierdb.catalog.Doctor
import java.awt.image.BufferedImage
import info.vizierdb.spark.udt.ImageUDT
import scala.sys.process.Process
import org.mimirdb.caveats.Caveat
import info.vizierdb.util.StringUtils
import info.vizierdb.spark.caveats.ExplainCaveats
import info.vizierdb.api.BrowseFilesystem
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.akka.VizierServer
import info.vizierdb.catalog.Metadata
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.plugin.sedona.VizierSedona

object Vizier
  extends LazyLogging
{
  var config: Config = null
  var sparkSession: SparkSession = null
  var urls: VizierURLs = null
  var mainClassLoader: ClassLoader = 
      Thread.currentThread().getContextClassLoader()
  val internalPlugins = Seq[Plugin](
    VizierSedona.plugin
  )

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

  def getProperty(p: String): Option[String] =
    config.properties.get(p)
          .orElse {
            CatalogDB.withDBReadOnly { implicit s =>
              Metadata.getOption(p)
            }
          }

  def initSpark() =
  {
    sparkSession = InitSpark.local
    InitSpark.initPlugins(sparkSession)
  }

  def initORMLogging(logLevel: String = "trace")
  {
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = true,
      logLevel = logLevel,
      warningThresholdMillis = 100
    ) 
  }

  def bringDatabaseToSaneState()
  {
    CatalogDB.withDB { implicit s => 
      Cell.abortEverything()
    }
  }

  def launchUIIfPossible(): Unit =
  {
    val command: Seq[String] = 
      System.getProperty("os.name").toLowerCase match {
        case "linux"  => Seq("xdg-open", urls.ui.toString)
        case "darwin" => Seq("open", urls.ui.toString)
        case _ => return
      }
    try {
      println("Opening your browser... (disable with '-n')")
      if(Process(command).! != 0){
        println(s"   ...opening your browser didn't work (${command.head} signaled an error)")
      }
    } catch {
      case e: Throwable => 
        println(s"   ...opening your browser didn't work (${e.getMessage()}")
    }
  }

  def loadPlugins(plugins: Seq[File]): Unit =
  {
    for(p <- plugins){ 
      println(s"  ...loading plugin $p")
      val plugin = Plugin.load(p)
      println(s"    ...loaded ${plugin.name}")
    }
  }

  def loadInternalPlugins(): Unit =
  {
    val classloader = this.getClass().getClassLoader()
    for(p <- internalPlugins){
      println(s"  ...loading builtin plugin ${p.name}")
      Plugin.load(classloader, p)
      println(s"    ...loaded ${p.name}")
    }
  }

  def setWorkingDirectory(): Unit =
  {
    if(config.workingDirectory.isDefined){
      val path = new File(config.workingDirectory()).getAbsolutePath()
      System.setProperty("user.dir", path)
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

    // Set up the working directory in environments and properties
    setWorkingDirectory()

    // Check for non-mandatory dependencies
    println("Checking for dependencies...")
    PythonProcess.checkPython()

    // Set up the Vizier directory and database
    println("Setting up project library...")
    if(!config.basePath().exists) { config.basePath().mkdir() }
    initSQLite()                // Connect to the DB
    Schema.initialize()         // Init the DB and/or apply schema migrations
    CatalogDB.initialize()      // Load ScalikeJDBC state
    bringDatabaseToSaneState()  // Clean up 'running' transactions (e.g., from a system crash)
    // initORMLogging("warn")

    // Set up Spark/Mimir/etc...
    println("Starting Spark...")
    initSpark()

    // Configure systemwide URLS
    urls = new VizierURLs(
      ui = new URL(VizierServer.publicURL),
      base = new URL(s"${VizierServer.publicURL}vizier-db/api/v1/"),
      api = Some(new URL(s"${VizierServer.publicURL}swagger/index.html"))
    )

    // Set up plugins
    loadInternalPlugins()
    if(!config.plugins.isEmpty){
      println("Loading plugins...")
      loadPlugins(config.plugins)
    }

    config.subcommand match {
      //////////////// HANDLE SPECIAL COMMANDS //////////////////
      case Some(subcommand) => 
        
        //////////////////// Ingest ////////////////////
        if(subcommand.equals(config.ingest)){
          try {
            val filePath = 
              config.resolveToWorkingDir(config.ingest.file())
            Streams.closeAfter(new FileInputStream(filePath)) { 
              ImportProject(
                _,
                execute = config.ingest.execute()
              )
            }
            println("Import complete.  Waiting for execution to finish...")
            Scheduler.joinAll()
            println("  ... import complete.")
            System.exit(0)
          } catch {
            case e:VizierException => 
              println(s"\nError: ${e.getMessage()}")
              System.exit(-1)
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
          VizierServer.run()
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
              project.artifacts
                     .toSeq
                     .filter { _._2.t.equals(ArtifactType.DATASET) }
                     .map { case (name, artifact) =>
                        name -> ExplainCaveats(
                          CatalogDB.withDB { implicit s => 
                            artifact.dataframe
                          }()
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
        VizierServer.run()

        if(!config.serverMode.getOrElse(false)){
          // Disable local filesystem browsing if running in server
          // mode.
          BrowseFilesystem.mountLocalFS()
        }
        BrowseFilesystem.mountPublishedArtifacts()

        println(s"... working directory < ${System.getProperty("user.dir")} >")
        println(s"... server running at < ${urls.ui} >")

        // Don't auto-launch the UI if we're asked not to
        // or if we're in server mode.
        if(!config.noUI() && !config.serverMode()){ launchUIIfPossible() }

        while(true){
          Thread.sleep(100000)
        }
    }
  }
}

