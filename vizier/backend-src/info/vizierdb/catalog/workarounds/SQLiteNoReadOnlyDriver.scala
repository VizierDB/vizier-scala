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
package info.vizierdb.catalog.workarounds

import java.sql.{ Driver, Connection }
import org.sqlite.{ JDBC => SQLiteDriver, SQLiteConnection }
import java.util.Properties
import java.util.logging.Logger
import java.sql.DriverPropertyInfo
import com.typesafe.scalalogging.LazyLogging
import java.sql.SQLException

object SQLiteNoReadOnlyDriver extends Driver
  with LazyLogging
{
  val SQLite = new SQLiteDriver()

  def acceptsURL(url: String): Boolean = 
    return url.startsWith("no-read-only:") && SQLite.acceptsURL(url.substring(13))
  def connect(url: String, info: Properties): Connection = 
  {
    logger.trace("URL: "+url.substring(13))
    val connection = SQLite.connect(url.substring(13), info)
    if(connection == null) {
      throw new SQLException(s"Problem connecting SQL $url")
    }
    return new SQLiteNoReadOnlyConnection(connection)
  }
  def getMajorVersion(): Int = SQLite.getMajorVersion()
  def getMinorVersion(): Int = SQLite.getMinorVersion()
  def getParentLogger(): Logger = SQLite.getParentLogger()
  def getPropertyInfo(x: String, y: Properties): Array[DriverPropertyInfo] = SQLite.getPropertyInfo(x, y)
  def jdbcCompliant(): Boolean = SQLite.jdbcCompliant()
}

class SQLiteNoReadOnlyConnection(sqlite: Connection)
  extends Connection
  with LazyLogging
{
  def abort(x: java.util.concurrent.Executor): Unit = { logger.trace("abort"); sqlite.abort(x) }
  def clearWarnings(): Unit = { logger.trace("clearWarnings"); sqlite.clearWarnings() }
  def close(): Unit = { logger.trace("close"); sqlite.close() }
  def commit(): Unit = { logger.trace("commit"); sqlite.commit() }
  def createArrayOf(x: String,y: Array[Object]): java.sql.Array = { logger.trace("createArrayOf"); sqlite.createArrayOf(x, y) }
  def createBlob(): java.sql.Blob = { logger.trace("createBlob"); sqlite.createBlob() }
  def createClob(): java.sql.Clob = { logger.trace("createClob"); sqlite.createClob() }
  def createNClob(): java.sql.NClob = { logger.trace("createNClob"); sqlite.createNClob() }
  def createSQLXML(): java.sql.SQLXML = { logger.trace("createSQLXML"); sqlite.createSQLXML() }
  def createStatement(x: Int,y: Int,z: Int): java.sql.Statement = { logger.trace("createStatement"); sqlite.createStatement(x, y, z) }
  def createStatement(x: Int,y: Int): java.sql.Statement = { logger.trace("createStatement"); sqlite.createStatement(x, y) }
  def createStatement(): java.sql.Statement = { logger.trace("createStatement"); sqlite.createStatement() }
  def createStruct(x: String,y: Array[Object]): java.sql.Struct = { logger.trace("createStruct"); sqlite.createStruct(x, y) }
  def getAutoCommit(): Boolean = { logger.trace("getAutoCommit"); sqlite.getAutoCommit() }
  def getCatalog(): String = { logger.trace("getCatalog"); sqlite.getCatalog() }
  def getClientInfo(): java.util.Properties = { logger.trace("getClientInfo"); sqlite.getClientInfo() }
  def getClientInfo(x: String): String = { logger.trace("getClientInfo"); sqlite.getClientInfo(x) }
  def getHoldability(): Int = { logger.trace("getHoldability"); sqlite.getHoldability() }
  def getMetaData(): java.sql.DatabaseMetaData = { logger.trace("getMetaData"); sqlite.getMetaData() }
  def getNetworkTimeout(): Int = { logger.trace("getNetworkTimeout"); sqlite.getNetworkTimeout() }
  def getSchema(): String = { logger.trace("getSchema"); sqlite.getSchema() }
  def getTransactionIsolation(): Int = { logger.trace("getTransactionIsolation"); sqlite.getTransactionIsolation() }
  def getTypeMap(): java.util.Map[String,Class[_]] = { logger.trace("getTypeMap"); sqlite.getTypeMap() }
  def getWarnings(): java.sql.SQLWarning = { logger.trace("getWarnings"); sqlite.getWarnings() }
  def isClosed(): Boolean = { logger.trace("isClosed"); sqlite.isClosed() }
  def isReadOnly(): Boolean = { logger.trace("isReadOnly"); sqlite.isReadOnly() }
  def isValid(x: Int): Boolean = { logger.trace("isValid"); sqlite.isValid(x) }
  def nativeSQL(x: String): String = { logger.trace("nativeSQL"); sqlite.nativeSQL(x) }
  def prepareCall(x: String,y: Int,z: Int,q: Int): java.sql.CallableStatement = { logger.trace("prepareCall"); sqlite.prepareCall(x, y, z, q) }
  def prepareCall(x: String,y: Int,z: Int): java.sql.CallableStatement = { logger.trace("prepareCall"); sqlite.prepareCall(x, y, z) }
  def prepareCall(x: String): java.sql.CallableStatement = { logger.trace("prepareCall"); sqlite.prepareCall(x) }
  def prepareStatement(x: String,y: Array[String]): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x, y) }
  def prepareStatement(x: String,y: Array[Int]): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x, y) }
  def prepareStatement(x: String,y: Int): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x, y) }
  def prepareStatement(x: String,y: Int,z: Int,q: Int): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x, y, z, q) }
  def prepareStatement(x: String,y: Int,z: Int): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x, y, z) }
  def prepareStatement(x: String): java.sql.PreparedStatement = { logger.trace("prepareStatement"); sqlite.prepareStatement(x) }
  def releaseSavepoint(x: java.sql.Savepoint): Unit = { logger.trace("releaseSavepoint"); sqlite.releaseSavepoint(x) }
  def rollback(x: java.sql.Savepoint): Unit = { logger.trace("rollback"); sqlite.rollback(x) }
  def rollback(): Unit = { logger.trace("rollback"); sqlite.rollback() }
  def setAutoCommit(x: Boolean): Unit = { logger.trace("setAutoCommit"); sqlite.setAutoCommit(x) }
  def setCatalog(x: String): Unit = { logger.trace("setCatalog"); sqlite.setCatalog(x) }
  def setClientInfo(x: java.util.Properties): Unit = { logger.trace("setClientInfo"); sqlite.setClientInfo(x) }
  def setClientInfo(x: String,y: String): Unit = { logger.trace("setClientInfo"); sqlite.setClientInfo(x, y) }
  def setHoldability(x: Int): Unit = { logger.trace("setHoldability"); sqlite.setHoldability(x) }
  def setNetworkTimeout(x: java.util.concurrent.Executor,y: Int): Unit = { logger.trace("setNetworkTimeout"); sqlite.setNetworkTimeout(x, y) }
  def setReadOnly(x: Boolean): Unit = { logger.trace("setReadOnly"); { /* WORKAROUND: IGNORE THIS */ } }
  def setSavepoint(x: String): java.sql.Savepoint = { logger.trace("setSavepoint"); sqlite.setSavepoint(x) }
  def setSavepoint(): java.sql.Savepoint = { logger.trace("setSavepoint"); sqlite.setSavepoint() }
  def setSchema(x: String): Unit = { logger.trace("setSchema"); sqlite.setSchema(x) }
  def setTransactionIsolation(x: Int): Unit = { logger.trace("setTransactionIsolation"); sqlite.setTransactionIsolation(x) }
  def setTypeMap(x: java.util.Map[String,Class[_]]): Unit = { logger.trace("setTypeMap"); sqlite.setTypeMap(x) }
  def isWrapperFor(x: Class[_]): Boolean = { logger.trace("isWrapperFor"); sqlite.isWrapperFor(x) }
  def unwrap[T](x: Class[T]): T = { logger.trace("unwrap"); sqlite.unwrap(x) }
}

