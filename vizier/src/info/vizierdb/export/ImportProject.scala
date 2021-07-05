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
package info.vizierdb.export

import scalikejdbc._
import play.api.libs.json._
import java.io.{ File, InputStream }
import org.apache.commons.compress.compressors.gzip._
import org.apache.commons.compress.archivers.tar._
import scala.collection.mutable.HashMap

import info.vizierdb.types._
import info.vizierdb.VizierException
import info.vizierdb.util.Streams
import info.vizierdb.viztrails.{ MutableProject, Scheduler }
import info.vizierdb.catalog._
import info.vizierdb.commands.Commands
import java.io.FileOutputStream
import info.vizierdb.util.StupidReactJsonField
import info.vizierdb.commands.{ FileParameter, FileArgument }
import com.typesafe.scalalogging.LazyLogging

object ImportProject
  extends LazyLogging
{
  val FILE = "fs/(.+)".r
  val VERSION = 1

  def hydrateModule(
    module: ExportedModule, 
    cache: HashMap[String, Module],
    files: Map[String, Artifact]
  )(implicit session: DBSession): Module =
  {
    val command = module.command
    cache.getOrElseUpdate(command.id.getOrElse { module.id }, {
      val arguments = 
        Commands.getOption(command.sanitizedPackageId, command.sanitizedCommandId)
                .getOrElse {
                  throw new VizierException(s"Cannot import.  Unsupported command ${command.packageId}.${command.commandId}")
                }
                .argumentsFromPropertyList(
                  command.arguments,
                  (param, arg) => {
                    param match {
                      case _:FileParameter => 
                        Json.toJson(
                          FileArgument(arg, 
                              fileid => 
                                files.get(fileid).getOrElse {
                                  throw new VizierException(s"Import error: Reference to unknown file with id $fileid")
                                }.id
                          )
                        )
                      case _ => arg
                    }
                  }
                )

      Module.make(
        packageId = command.sanitizedPackageId,
        commandId = command.sanitizedCommandId,
        properties = JsObject(command.properties.getOrElse { Map() }),
        revisionOfId = command.revisionOfId.map { cache(_).id },
        arguments = arguments
      )
    })
  }

  def apply(input: InputStream, execute: Boolean = false, blockOnExecution: Boolean = true): Identifier = 
  {
    val gz = new GzipCompressorInputStream(input)
    val tar = new TarArchiveInputStream(gz)
    var entry = tar.getNextTarEntry()
    var version: Int = -1
    var exportedProject: ExportedProject = null
    val rawFiles = HashMap[String, File]()

    def getStream(): InputStream = {
      if(!tar.canReadEntryData(entry)){
        throw new VizierException("Corrupted Archive")
      }
      tar
    }
    def getData(): Array[Byte] = {
      Streams.readAll(getStream)
    }

    try { 
      while( entry != null ){
        entry.getName match {
          //////////////////////////////////////
          case FILE(id) => {
            val temp = File.createTempFile("vizier", ".dat")
            Streams.cat(getStream(), new FileOutputStream(temp))
            rawFiles.put(id, temp)
          }
          //////////////////////////////////////
          case "version.txt" => {
            version = new String(getData()).toInt
            if(version > VERSION){
              throw new VizierException("Archive version is too new")
            }
            logger.info(s"Version of imported project is ${version}")
          }
          //////////////////////////////////////
          case "project.json" => {
            exportedProject = Json.parse(getData()).as[ExportedProject]
          }
          //////////////////////////////////////
        }
        entry = tar.getNextTarEntry()
      }
      if(version < 0 || exportedProject == null){
        throw new VizierException("Not a valid archive (missing version.txt)")
      }


      val projectId = 
        DB.autoCommit { implicit s => 
          Project.create(
            name = exportedProject.name,
            properties = JsObject(exportedProject.propertyMap),
            createdAt  = Some(exportedProject.createdAt),
            modifiedAt = Some(exportedProject.lastModifiedAt),
            dontCreateDefaultBranch = true
          ).id
        }
      logger.info(s"Importing project '${exportedProject.name} as ID $projectId")
      val mutableProject = MutableProject(projectId)

      val files = 
        exportedProject
          .files
          .map { file => 
            val fileArtifact = 
              mutableProject.addFile(
                file = rawFiles(file.id),
                name = Some(file.name),
                mimetype = Some(file.mimetype.getOrElse { MIME.TEXT })
              )
            logger.info(s"Imported file ${file.id} as ${fileArtifact.absoluteFile} (${file.name})")
            file.id -> fileArtifact
          }
          .toMap

      // The next bit involves a level of surgery that we don't really
      // want to do in general ... it has the potential to create all sorts
      // of nonsense.  So we do most of the DB access directly from this point
      // onwards.
      var project = mutableProject.project
      val modules = HashMap[String,Module]()
      val branches = HashMap[String,Branch]()
      val workflows = HashMap[(String,String),Workflow]()
      DB.autoCommit { implicit s => 
        for( exportedBranch <- exportedProject.branches ){


          if(exportedBranch.sourceBranch.isDefined != exportedBranch.sourceWorkflow.isDefined)
          {
            throw new VizierException(s"Corrupted Import: Branch ${exportedBranch.id} was derived from Branch ${exportedBranch.sourceBranch} / Workflow ${exportedBranch.sourceWorkflow}")
          }
          var (_, branch, _) =
            project.createBranch(
              name = exportedBranch.name,
              properties = JsObject(exportedBranch.propertyMap),
              isInitialBranch = exportedBranch.sourceBranch.isEmpty,
              fromBranch = exportedBranch.sourceBranch.map { branches(_).id },
              fromWorkflow = exportedBranch.sourceBranch
                                           .zip(exportedBranch.sourceWorkflow)
                                           .map { workflows(_).id }
                                           .headOption,
              skipWorkflowInitialization = true,
              createdAt = Some(exportedBranch.createdAt),
              modifiedAt = Some(exportedBranch.lastModifiedAt)
            )
          logger.debug(s"Importing branch ${exportedBranch.name}[${exportedBranch.id}] as ID ${branch.id}")

          branches.put(exportedBranch.id, branch)

          var workflow:Workflow = 
            if(exportedBranch.sourceBranch.isEmpty){
              branch.initWorkflow()._2 // default blank workflow
            } else { 
              val sourceWorkflow = 
                workflows( (
                  exportedBranch.sourceBranch.get,
                  exportedBranch.sourceWorkflow.get
                ) )

              branch.cloneWorkflow(sourceWorkflow.id)._2
            }

          for( exportedWorkflow <- exportedBranch.workflows ){

            val (updatedBranch, updatedWorkflow) = branch.initWorkflow(
              prevId = Some(workflow.id),
              action = exportedWorkflow.decodedAction,
              actionModuleId = exportedWorkflow.actionModule.map { modules(_).id },
              setHead = false,
              createdAt = Some(exportedWorkflow.createdAt)
            )
            branch = updatedBranch
            workflows.put(
              (exportedBranch.id, exportedWorkflow.id),
              updatedWorkflow
            )
            logger.debug(s"Importing workflow [${exportedBranch.id}/${exportedWorkflow.id}] as ID ${updatedWorkflow.id}")

            // next create the cells
            for( (exportedModuleRef, position) <- exportedWorkflow.modules.zipWithIndex) 
            {
              val exportedModule = exportedProject.modulesByNewVizierId(exportedModuleRef)
              val module = hydrateModule(exportedModule, modules, files)

              // Ok, here's where the translation from Vizier classic gets
              // a little clunky.  Translating the naming from the classic
              // notation we use for the export classes to the catalog classes:
              //   exportedModule.id is the Result.id 
              //   exportedModule.command.id is the Module.id
              // However, Vizier classic doesn't make this distinction, so
              // we have exportedModule.command.id == None, and we default
              // to using exportedModule.id for both

              // TODO : create a result object
              Cell.make(
                workflowId = updatedWorkflow.id,
                position = position,
                moduleId = module.id,
                resultId = None,
                state = ExecutionState.CANCELLED,
                created = exportedModule.timestamps.createdAt
              )
            }


            workflow = updatedWorkflow

            logger.trace( s"Importing ${exportedWorkflow.action}(${(exportedWorkflow.packageId.toSeq++exportedWorkflow.commandId).mkString(".")})" )
          }
          Branch.setHead(branch.id, workflow.id)
        }

        logger.info( s"Activating branch ${branches(exportedProject.defaultBranch).id}" )
        project = project.activateBranch(
          branches(exportedProject.defaultBranch).id
        )
      }

      if(execute){
        logger.info("Triggering workflow execution")
        val mutableProject = MutableProject(project.id)
        val head = mutableProject.head
        DB.autoCommit { implicit s => head.discardResults() }
        Scheduler.schedule(head.id)
        if(blockOnExecution){
          mutableProject.waitUntilReadyAndThrowOnError
        }
      }
      return projectId
      // logger.info(exported.as[Map[String,JsValue]].keys.mkString("\n"))
    } finally { 
      for(file <- rawFiles.values){ if(file.exists) { file.delete() } }
    }
  }
}

