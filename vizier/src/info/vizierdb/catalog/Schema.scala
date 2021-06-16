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
package info.vizierdb.catalog

import info.vizierdb.catalog.migrations._
import scalikejdbc._
import scalikejdbc.metadata._
import java.sql.{ Types => SQL, SQLException }
import com.typesafe.scalalogging.LazyLogging

object Schema
  extends LazyLogging
{
  def schemaVersion: Int = 
  {
    if(DB.getTable("metadata").isEmpty){ return 0 }
    else { 
      DB.readOnly { implicit session => 
        Metadata.getOption("schema") 
      }.map { _.toInt }.getOrElse { 0 } 
    }
  }

  def initialize()
  {
    migrateToCurrentVersion()
  }
  def drop = 
  {
    val currentVersion = schemaVersion
    DB autoCommit { implicit session => 
      for(migration <- MIGRATIONS.take(currentVersion).reverse){
        try {
          migration.drop
        } catch {
          case e: Exception =>  
            logger.error(s"Error dropping $migration: ${e.getMessage()}")
        } 
      }
    }
  }

  def migrateToCurrentVersion()
  {
    val currentVersion = schemaVersion
    val requiredMigrations = MIGRATIONS.drop(currentVersion)
    if(requiredMigrations.isEmpty){ return }

    DB autoCommit { implicit session => 
      for((migration, idx) <- requiredMigrations.zipWithIndex){
        logger.info(s"Applying Migration ${idx + currentVersion}")
        logger.trace(migration.sql)
        migration.apply
      }

      Metadata.put("schema", MIGRATIONS.size.toString) 
    }
  }

  def columns(table: String): Seq[String] =
    TABLES(table.toLowerCase()).columns.map { _.name }

  val MIGRATIONS = Seq[Migration](
    ///////////////////// Metadata ///////////////////// 
    CreateTableMigration(Table(
      name = "Metadata",
      columns = List(
        Column("key",             SQL.VARCHAR,  "varchar(20)",  isRequired = true,
                                                                isPrimaryKey = true),
        Column("value",           SQL.VARCHAR,  "varchar(255)", isRequired = true)
      )
    )),
    ///////////////////// Projects ///////////////////// 
    CreateTableMigration(Table( 
      name = "Project",
      columns = List(
        Column("id",              SQL.INTEGER,  "integer",      isRequired = true,
                                                                isPrimaryKey = true,
                                                                isAutoIncrement = true),
        Column("name",            SQL.VARCHAR,  "varchar(255)", isRequired = true),
        Column("active_branch_id",SQL.INTEGER,  "int",          isRequired = false),
        Column("properties",      SQL.BLOB,     "text",         isRequired = false),
        Column("created",         SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("modified",        SQL.TIMESTAMP,"timestamp",    isRequired = true)
      )
    )),
    ///////////////////// Branches ///////////////////// 
    CreateTableMigration(Table(
      name = "Branch",
      columns = List(
        Column("id",                       SQL.INTEGER,  "integer",      isRequired = true,
                                                                         isPrimaryKey = true,
                                                                         isAutoIncrement = true),
        Column("project_id",               SQL.INTEGER,  "integer",      isRequired = true),
        Column("name",                     SQL.VARCHAR,  "varchar(255)", isRequired = true),
        Column("properties",               SQL.BLOB,     "text",         isRequired = false),
        Column("head_id",                  SQL.INTEGER,  "integer",      isRequired = true),
        Column("created",                  SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("modified",                 SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("created_from_branch_id",   SQL.INTEGER,  "integer",      isRequired = false),
        Column("created_from_workflow_id", SQL.INTEGER,  "integer",      isRequired = false),
      )
    )),
    ///////////////////// Workflows ///////////////////// 
    CreateTableMigration(Table(
      name = "Workflow",
      columns = List(
        Column("id",              SQL.INTEGER,  "integer",      isRequired = true,
                                                                isPrimaryKey = true,
                                                                isAutoIncrement = true),
        Column("prev_id",         SQL.INTEGER,  "integer",      isRequired = false),
        Column("branch_id",       SQL.INTEGER,  "integer",      isRequired = true),
        Column("action",          SQL.INTEGER,  "integer",      isRequired = false),
        Column("action_module_id",SQL.INTEGER,  "integer",      isRequired = false),
        Column("created",         SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("aborted",         SQL.SMALLINT, "smallint",     isRequired = true)
      )
    )),
    ///////////////////// Modules ///////////////////// 
    CreateTableMigration(Table(
      name = "Module",
      columns = List(
        Column("id",              SQL.INTEGER,  "integer",      isRequired = true,
                                                                isPrimaryKey = true,
                                                                isAutoIncrement = true),
        Column("package_id",      SQL.VARCHAR,  "varchar(30)",  isRequired = false),
        Column("command_id",      SQL.VARCHAR,  "varchar(30)",  isRequired = true),
        Column("arguments",       SQL.BLOB,     "text",         isRequired = true),
        Column("properties",      SQL.INTEGER,  "text",         isRequired = false),
        Column("revision_of_id",  SQL.INTEGER,  "integer",      isRequired = false)
      )
    )),
    ///////////////////// Cells ///////////////////// 
    CreateTableMigration(Table(
      name = "Cell",
      columns = List(
        Column("workflow_id",     SQL.INTEGER,  "integer",      isRequired = true, 
                                                                isPrimaryKey = true),
        Column("position",        SQL.INTEGER,  "integer",      isRequired = false,
                                                                isPrimaryKey = true),
        Column("module_id",       SQL.INTEGER,  "integer",      isRequired = true),
        Column("result_id",       SQL.INTEGER,  "integer",      isRequired = false),
        Column("state",           SQL.SMALLINT, "smallint",     isRequired = true),
        Column("created",         SQL.TIMESTAMP,"timestamp",    isRequired = true)
      )
    )),
    ///////////////////// Artifact ///////////////////// 
    CreateTableMigration(Table(
      name = "Artifact",
      columns = List(
        Column("id",              SQL.INTEGER,  "integer",      isRequired = true, 
                                                                isPrimaryKey = true,
                                                                isAutoIncrement = true),
        Column("project_id",      SQL.INTEGER,  "integer",      isRequired = true),
        Column("t",               SQL.INTEGER,  "integer",      isRequired = false),
        Column("mime_type",       SQL.VARCHAR,  "varchar(255)", isRequired = false),
        Column("created",         SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("data",            SQL.BLOB,     "text",         isRequired = false),
      )
    )),
    ///////////////////// Output ///////////////////// 
    CreateTableMigration(Table(
      name = "Output", 
      columns = List(
        Column("result_id",       SQL.INTEGER,  "integer",      isRequired = false, 
                                                                isPrimaryKey = true),
        Column("user_facing_name",SQL.VARCHAR,  "varchar(255)", isRequired = false,
                                                                isPrimaryKey = true),
        Column("artifact_id",     SQL.INTEGER,  "integer",      isRequired = false),
      )
    )),
    ///////////////////// Input ///////////////////// 
    CreateTableMigration(Table(
      name = "Input",
      columns = List(
        Column("result_id",       SQL.INTEGER,  "integer",      isRequired = false, 
                                                                isPrimaryKey = true),
        Column("user_facing_name",SQL.VARCHAR,  "varchar(255)", isRequired = false,
                                                                isPrimaryKey = true),
        Column("artifact_id",     SQL.INTEGER,  "integer",      isRequired = true),
      )
    )),
    ///////////////////// Message ///////////////////// 
    CreateTableMigration(Table(
      name = "Message",
      columns = List(
        Column("result_id",       SQL.INTEGER,  "integer",      isRequired = true),
        Column("mime_type",       SQL.VARCHAR,  "varchar(30)",  isRequired = false),
        Column("data",            SQL.BLOB,     "text",         isRequired = false),
        Column("stream",          SQL.INTEGER,  "integer",      isRequired = true),
      )
    )),
    ///////////////////// Result ///////////////////// 
    CreateTableMigration(Table(
      name = "Result",
      columns = List(
        Column("id",              SQL.INTEGER,  "integer",      isRequired = true, 
                                                                isPrimaryKey = true,
                                                                isAutoIncrement = true),
        Column("started",         SQL.TIMESTAMP,"timestamp",    isRequired = true),
        Column("finished",        SQL.TIMESTAMP,"timestamp",    isRequired = false)
      )
    ))
  )

  val TABLES: Map[String, Table] =
    MIGRATIONS.foldLeft(Map[String, Table]()){ case (sch, mig) => mig.updateSchema(sch) }

}

