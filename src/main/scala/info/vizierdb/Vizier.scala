package info.vizierdb

import scalikejdbc._
import java.sql.DriverManager

import org.mimirdb.api.{ MimirAPI, InitSpark }
import org.mimirdb.data.{ JDBCMetadataBackend => MimirJDBC, Catalog => MimirCatalog }

import info.vizierdb.types._
import info.vizierdb.catalog.workarounds.SQLiteNoReadOnlyDriver
import info.vizierdb.catalog.{ Project, Schema }
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
      new LocalFSStagingProvider(stagingDirectory),
      MimirAPI.sparkSession
    )
    MimirAPI.runServer(MimirAPI.DEFAULT_API_PORT) // Starts the Mimir server **in the background**
  }

  def createProject(name: String) =
    DB.autoCommit { implicit s => Project.create(name) }
  
  def getProject(id: Identifier) =
    DB.readOnly { implicit s => Project.get(id) }

  def main(args: Array[String]) 
  {
    println("Starting SQLite...")
    initSQLite()
    Schema.initialize()
    // createProject("TEST PROJECT")
    println("Starting Mimir...")
    initMimir()
    println("Starting Server...")
    VizierAPI.init()
    println("... Server running!")
    VizierAPI.server.join()
  }
}