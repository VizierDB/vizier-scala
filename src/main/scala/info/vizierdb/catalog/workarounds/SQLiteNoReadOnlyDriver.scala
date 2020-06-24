package info.vizierdb.catalog.workarounds

import java.sql.{ Driver, Connection }
import org.sqlite.{ JDBC => SQLiteDriver, SQLiteConnection }
import java.util.Properties
import java.util.logging.Logger
import java.sql.DriverPropertyInfo
import com.typesafe.scalalogging.LazyLogging

object SQLiteNoReadOnlyDriver extends Driver
  with LazyLogging
{
  val SQLite = new SQLiteDriver()

  def acceptsURL(url: String): Boolean = 
    return url.startsWith("no-read-only:") && SQLite.acceptsURL(url.substring(13))
  def connect(url: String, info: Properties): Connection = 
  {
    logger.trace("URL: "+url.substring(13))
    return new SQLiteNoReadOnlyConnection(SQLite.connect(url.substring(13), info))
  }
  def getMajorVersion(): Int = SQLite.getMajorVersion()
  def getMinorVersion(): Int = SQLite.getMinorVersion()
  def getParentLogger(): Logger = SQLite.getParentLogger()
  def getPropertyInfo(x: String, y: Properties): Array[DriverPropertyInfo] = SQLite.getPropertyInfo(x, y)
  def jdbcCompliant(): Boolean = SQLite.jdbcCompliant()
}

class SQLiteNoReadOnlyConnection(sqlite: Connection)
  extends Connection
{
  def abort(x: java.util.concurrent.Executor): Unit = sqlite.abort(x)
  def clearWarnings(): Unit = sqlite.clearWarnings()
  def close(): Unit = sqlite.close()
  def commit(): Unit = sqlite.commit()
  def createArrayOf(x: String,y: Array[Object]): java.sql.Array = sqlite.createArrayOf(x, y)
  def createBlob(): java.sql.Blob = sqlite.createBlob()
  def createClob(): java.sql.Clob = sqlite.createClob()
  def createNClob(): java.sql.NClob = sqlite.createNClob()
  def createSQLXML(): java.sql.SQLXML = sqlite.createSQLXML()
  def createStatement(x: Int,y: Int,z: Int): java.sql.Statement = sqlite.createStatement(x, y, z)
  def createStatement(x: Int,y: Int): java.sql.Statement = sqlite.createStatement(x, y)
  def createStatement(): java.sql.Statement = sqlite.createStatement()
  def createStruct(x: String,y: Array[Object]): java.sql.Struct = sqlite.createStruct(x, y)
  def getAutoCommit(): Boolean = sqlite.getAutoCommit()
  def getCatalog(): String = sqlite.getCatalog()
  def getClientInfo(): java.util.Properties = sqlite.getClientInfo()
  def getClientInfo(x: String): String = sqlite.getClientInfo(x)
  def getHoldability(): Int = sqlite.getHoldability()
  def getMetaData(): java.sql.DatabaseMetaData = sqlite.getMetaData()
  def getNetworkTimeout(): Int = sqlite.getNetworkTimeout()
  def getSchema(): String = sqlite.getSchema()
  def getTransactionIsolation(): Int = sqlite.getTransactionIsolation()
  def getTypeMap(): java.util.Map[String,Class[_]] = sqlite.getTypeMap()
  def getWarnings(): java.sql.SQLWarning = sqlite.getWarnings()
  def isClosed(): Boolean = sqlite.isClosed()
  def isReadOnly(): Boolean = sqlite.isReadOnly()
  def isValid(x: Int): Boolean = sqlite.isValid(x)
  def nativeSQL(x: String): String = sqlite.nativeSQL(x)
  def prepareCall(x: String,y: Int,z: Int,q: Int): java.sql.CallableStatement = sqlite.prepareCall(x, y, z, q)
  def prepareCall(x: String,y: Int,z: Int): java.sql.CallableStatement = sqlite.prepareCall(x, y, z)
  def prepareCall(x: String): java.sql.CallableStatement = sqlite.prepareCall(x)
  def prepareStatement(x: String,y: Array[String]): java.sql.PreparedStatement = sqlite.prepareStatement(x, y)
  def prepareStatement(x: String,y: Array[Int]): java.sql.PreparedStatement = sqlite.prepareStatement(x, y)
  def prepareStatement(x: String,y: Int): java.sql.PreparedStatement = sqlite.prepareStatement(x, y)
  def prepareStatement(x: String,y: Int,z: Int,q: Int): java.sql.PreparedStatement = sqlite.prepareStatement(x, y, z, q)
  def prepareStatement(x: String,y: Int,z: Int): java.sql.PreparedStatement = sqlite.prepareStatement(x, y, z)
  def prepareStatement(x: String): java.sql.PreparedStatement = sqlite.prepareStatement(x)
  def releaseSavepoint(x: java.sql.Savepoint): Unit = sqlite.releaseSavepoint(x)
  def rollback(x: java.sql.Savepoint): Unit = sqlite.rollback(x)
  def rollback(): Unit = sqlite.rollback()
  def setAutoCommit(x: Boolean): Unit = sqlite.setAutoCommit(x)
  def setCatalog(x: String): Unit = sqlite.setCatalog(x)
  def setClientInfo(x: java.util.Properties): Unit = sqlite.setClientInfo(x)
  def setClientInfo(x: String,y: String): Unit = sqlite.setClientInfo(x, y)
  def setHoldability(x: Int): Unit = sqlite.setHoldability(x)
  def setNetworkTimeout(x: java.util.concurrent.Executor,y: Int): Unit = sqlite.setNetworkTimeout(x, y)
  def setReadOnly(x: Boolean): Unit = { /* WORKAROUND: IGNORE THIS */ }
  def setSavepoint(x: String): java.sql.Savepoint = sqlite.setSavepoint(x)
  def setSavepoint(): java.sql.Savepoint = ???
  def setSchema(x: String): Unit = sqlite.setSchema(x)
  def setTransactionIsolation(x: Int): Unit = sqlite.setTransactionIsolation(x)
  def setTypeMap(x: java.util.Map[String,Class[_]]): Unit = sqlite.setTypeMap(x)
  def isWrapperFor(x: Class[_]): Boolean = sqlite.isWrapperFor(x)
  def unwrap[T](x: Class[T]): T = sqlite.unwrap(x)
}
