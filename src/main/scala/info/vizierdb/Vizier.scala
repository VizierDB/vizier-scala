package info.vizierdb

import scalikejdbc._
import java.sql.DriverManager

import org.mimirdb.api.{ MimirAPI, InitSpark, MimirConfig }
import org.mimirdb.data.{ JDBCMetadataBackend => MimirJDBC, Catalog => MimirCatalog }

import info.vizierdb.types._
import info.vizierdb.catalog.workarounds.SQLiteNoReadOnlyDriver
import info.vizierdb.catalog.{ Project, Schema, Cell }
import org.mimirdb.data.LocalFSStagingProvider
import java.io.File

object Vizier
{
  var basePath = { val d = new File(".vizierdb"); if(!d.exists()){ d.mkdir() }; d }

  def initSQLite(db: String = "Vizier.db") = 
  {
    // Instead of using the default SQLite driver, we're going to use the following workaround.
    // Specifically, The SQLite driver doesn't like it when you change the READ-ONLY status of a 
    // connection once it's open, while also not liking > 1 connection open and committing at the
    // same time.  Instead, we're going to simply disable READ-ONLY mode.  This is going to be
    // slightly slower, 
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    ConnectionPool.singleton(
      url = "no-read-only:jdbc:sqlite:" + new File(basePath, db).toString,
      user = "",
      password = "",
      settings = ConnectionPoolSettings(
        initialSize = 1,
        maxSize = 1,
        connectionTimeoutMillis = 1000l
      )
    )
  }

  def initMimir(db: String = "Mimir.db", stagingDirectory: String = ".") =
  {
    MimirAPI.sparkSession = InitSpark.local
    InitSpark.initPlugins(MimirAPI.sparkSession)
    MimirAPI.metadata = new MimirJDBC("sqlite", new File(basePath, db).toString)
    MimirAPI.catalog = new MimirCatalog(
      MimirAPI.metadata,
      new LocalFSStagingProvider(new File(basePath, stagingDirectory).toString),
      MimirAPI.sparkSession
    )
    MimirAPI.runServer(MimirAPI.DEFAULT_API_PORT) // Starts the Mimir server **in the background**
    MimirAPI.conf = 
      new MimirConfig(Seq(
        "--python", info.vizierdb.commands.python.PythonProcess.PYTHON_COMMAND,
        "--data-dir", new File(basePath, "mimir_data").toString,
        "--staging-dir", new File(basePath, stagingDirectory).toString
      ))
    MimirAPI.conf.verify
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
    println("Starting SQLite...")
    initSQLite()
    Schema.initialize()
    initORMLogging()
    bringDatabaseToSaneState()
    println("Starting Mimir...")
    initMimir()
    println("Starting Server...")
    VizierAPI.init()
    println("... Server running!")
    println(s" Launch Vizier By Going to < ${VizierAPI.urls.ui} >")
    VizierAPI.server.join()
  }
}