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
import java.io.{ OutputStream, FileInputStream, File }
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.archivers.tar._
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.util._
import scala.collection.mutable.HashMap
import info.vizierdb.commands.Commands
import info.vizierdb.VizierException

object ExportProject
{
  def apply(projectId: Identifier, output: OutputStream) =
  {
    val gz = new GzipCompressorOutputStream(output)
    val tar = new TarArchiveOutputStream(gz)

    def writeData(name: String, data: Array[Byte]) = 
    {
      val entry = new TarArchiveEntry(name)
      entry.setSize(data.size)
      tar.putArchiveEntry(entry)
      tar.write(data)
      tar.closeArchiveEntry()
    }
    def writeFile(name: String, file: File) =
    {
      val entry = new TarArchiveEntry(name)
      entry.setSize(file.length())
      tar.putArchiveEntry(entry)
      Streams.closeAfter(new FileInputStream(file)){
        Streams.cat(_, tar)
      }
      tar.closeArchiveEntry()
    }

    writeData("version.txt", ImportProject.VERSION.toString.getBytes)

    // File Artifacts
    DB.readOnly { implicit s => 
      val p = 
        Project.lookup(projectId)
               .getOrElse { 
                 throw new VizierException(s"No project with ID $projectId")
               }
      val files: Seq[FileSummary] = (
        p.artifacts
          .filter { _.t == ArtifactType.FILE }
          .filter { !_.file.isDirectory() }
          .map { artifact => 
            val f = artifact.file
            writeFile(s"fs/${artifact.id}", f)

            FileSummary(
              id = artifact.id.toString,
              name = try {
                (Json.parse(artifact.data) \ "filename")
                          .asOpt[String]
                          .getOrElse { s"file_${artifact.id}" }
              } catch {
                case e: Throwable => 
                  s"file_${artifact.id}"
              },
              mimetype = Some(artifact.mimeType)
            )
          }
      )

      val modules = HashMap[Identifier,ExportedModule]()

      val branches:Seq[ExportedBranch] = 
        p.branches
         .map { branch => 
            val workflows: Seq[ExportedWorkflow] = 
              branch.workflows
                    .map { workflow => 

                      val cells:Seq[Identifier] = 
                        workflow.cellsAndModulesInOrder
                                .map { case (cell, module) => 
                                  // State schema conventions underwent a little 
                                  // revamp in Vizier-scala.  Specifically, now
                                  // what used to be called a "Command" is now
                                  // a Module, and what used to be called a 
                                  // "Module" is now split between Module and Cell.
                                  // The export schema follows Vizier-classic
                                  // conventions for compatibility, with the 
                                  // following hack: if command.id.isDefined, then
                                  // this will be used as the module identifier.

                                  val cell_id = s"${workflow.id}_${cell.position}"
                                  val arguments = 
                                    module.command
                                          .get
                                          .encodeReactArguments(module.arguments)

                                  if(!modules.contains(module.id)) {
                                    modules += module.id -> 
                                      ExportedModule(
                                        id = cell_id,
                                        state = ExecutionState.translateToClassicVizier(cell.state),
                                        command = ExportedCommand(
                                          id = Some(module.id.toString),
                                          packageId = module.packageId,
                                          commandId = module.commandId,
                                          arguments = arguments,
                                          revisionOfId = module.revisionOfId
                                                                .map { _.toString},
                                          properties = Some(module.properties.value.toMap)
                                        ),
                                        text = JsString(module.description),
                                        timestamps = ExportedTimestamps(
                                          createdAt = cell.created,
                                          startedAt = None,
                                          finishedAt = None,
                                          lastModifiedAt = None,
                                        )
                                      )

                                  }
                                  module.id
                                }


                      val actionModule: Option[Module] = 
                        workflow.actionModuleId.map { Module.get(_) }

                      ExportedWorkflow(
                        id = workflow.id.toString,
                        createdAt = workflow.created,
                        action = ActionType.encode(workflow.action),
                        packageId = actionModule.map { _.packageId },
                        commandId = actionModule.map { _.commandId },
                        actionModule = actionModule.map { _.id.toString },
                        modules = cells.map { _.toString }
                      )

                    }

            ExportedBranch(
              id = branch.id.toString,
              createdAt = branch.created,
              lastModifiedAt = branch.modified,
              sourceBranch = branch.createdFromBranchId.map { _.toString },
              sourceWorkflow = branch.createdFromWorkflowId.map { _.toString },
              sourceModule = branch.createdFromModuleId.map { _.toString },
              isDefault = (branch.id == p.activeBranchId),
              properties = StupidReactJsonMap(branch.properties.value.toMap),
              workflows = workflows
            )
         }

      // sanity check
      for(branch <- branches){
        for(workflow <- branch.workflows){
          for(module <- workflow.modules){
            assert(modules contains module.toLong, s"Module $module in workflow ${workflow.id} did not get properly exported")

          }
          // for(module <- workflow.actionModule){
          //   assert(modules contains module.toLong, s"Action module $module for workflow ${workflow.id} did not get properly exported")
          // }
        }
      }


      val project: JsValue = 
        Json.toJson(
          ExportedProject(
            properties = StupidReactJsonMap(p.properties.value.toMap),
            defaultBranch = p.activeBranchId.toString,
            files = files,
            modules = modules.map { case (k, v) => k.toString -> v }.toMap,
            branches = branches,
            createdAt = p.created,
            lastModifiedAt = p.modified
          )
        )

      writeData("project.json", project.toString.getBytes)
      tar.close()
    }
  }

}

