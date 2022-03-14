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
package info.vizierdb.viztrails.graph

import collection.mutable.HashMap
import scalikejdbc.DB
import info.vizierdb.catalog._
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging

object WorkflowTrace
  extends LazyLogging
{
  def apply(
    projectId: Identifier, 
    branchId: Identifier, 
    workflowId: Option[Identifier],
    includeArtifacts: Boolean = true
  ): String =
  {
    DB.readOnly { implicit s => 
      val branch = Branch.get(branchId)
      if(branch.projectId != projectId) {
        throw new IndexOutOfBoundsException() 
      }
      val workflow = 
        workflowId.map { Workflow.get(_) }
                  .getOrElse( branch.head )
      if(workflow.branchId != branchId) {
        throw new IndexOutOfBoundsException() 
      }
      logger.debug(s"Workflow: $workflow")

      val svg = new Svg(400, 0)

      svg.stroke = "black"
      svg.strokeWidth = 1
      svg.fill = "white"

      var offsetY = 10

      /** A 2-tuple of the index of a variable, and whether it has been deleted */
      val scope = HashMap[String, (Int, Boolean)]()
      val lastUsed = HashMap[Int, Int]()
      val artifactWidth = 180
      def artifactX(idx: Int) = (320+idx*(artifactWidth+20))
      val ignorePackages = Set("docs")
      var lastVizualDataframe: Option[String] = None
      val pipeWidth = 3
      val pipeStroke = "grey"

      for((cell, module) <- workflow.cellsAndModulesInOrder)
      {
        logger.debug(s"${cell.position}: $module")
        var skip = false
        var description = s"${module.packageId}.${module.commandId}"
        if(ignorePackages(module.packageId)){ skip = true }
        if(module.packageId.equals("vizual")){
          if(lastVizualDataframe.equals(
                (module.arguments \ "dataset").asOpt[String]))
          { 
            skip = true 
          } else { 
            description = "vizual script"
            lastVizualDataframe = (module.arguments \ "dataset").asOpt[String] 
          }
        } else { lastVizualDataframe = None }


        if(!skip){
          var bottom = 0

          svg.g { g => 

            val inputs = cell.inputs
                             .map { a => a.userFacingName.toLowerCase -> a.artifactId.get }
                             .toMap
            val (outputs, deletions) = { 
              val (outputs, deletions) = cell.outputs
                                             .partition { _.artifactId.isDefined }
              (
                outputs.map { a => a.userFacingName.toLowerCase -> a.artifactId.get }
                       .toMap
                       .filterKeys { !_.equals("temporary_dataset") },
                deletions.map { _.userFacingName }.toSet
              )
            }
            val newOutputs = outputs.filter { case (k, _) => 
                                scope.get(k)
                                      // If an entry exists in the scope, the
                                      // key is new if the entry was previously
                                      // deleted
                                     .map { !_._2 }
                                      // Otherwise the entry is definitely new.
                                     .getOrElse(true)
                             }
            val transforms = outputs.keySet -- newOutputs.keySet
            val nonTransformInputs = inputs.keySet -- transforms

            for(del <- deletions) {
              scope(del) = (scope(del)._1, false)
            }
            val passThroughs:Set[Int] = 
              scope.filter { _._2._2 }
                   .toMap
                   .mapValues { _._1 }
                   .filterKeys { k => !outputs.contains(k) || !deletions(k) }
                   .values.toSet
            for((out, _) <- newOutputs){
              if(scope.contains(out)){
                scope(out) = (scope(out)._1, true)
              } else {
                scope(out) = (scope.size, true)
              }
            }


            g.translate(0, offsetY)

            if(!nonTransformInputs.isEmpty){
              val artifacts = 
                nonTransformInputs.toSeq
                                  .map { scope(_)._1 }
                                  .sorted
              g.path { 
                _.stroke(pipeStroke)
                 .strokeWidth(pipeWidth)
                 .move(150, 30)
                 .lineOffset(15, -15)
                 .lineX(artifactX(artifacts.max)+artifactWidth/2-15)
              }
              for( idx <- artifacts ){
                svg.path { 
                    _.stroke(pipeStroke)
                     .strokeWidth(pipeWidth)
                     .move(
                        artifactX(idx)+artifactWidth/2, 
                        lastUsed(idx)
                      )
                     .lineY(offsetY+bottom+30)                
                }
                g.path { 
                  _.stroke(pipeStroke)
                   .strokeWidth(pipeWidth)
                   .move(artifactX(idx)+artifactWidth/2-15, 15)
                   .lineOffset(15, -15)
                }
                lastUsed(idx) = offsetY
              }
              bottom += 30
            }

            g.g { g => 
              g.translate(0, bottom)
              g.rect(10, 0, 300, 30)
              g.text(15, 20, cell.position.toString)
              g.text(65, 20, description)

              if(!transforms.isEmpty){
                val artifacts = 
                  transforms.toSeq
                            .map { scope(_)._1 }
                            .sorted

                g.path { 
                  _.stroke(pipeStroke)
                   .strokeWidth(pipeWidth)
                   .fill("none")
                   .move(310, 15)
                   .lineX(artifactX(artifacts.max)+artifactWidth/2-10)
                   .lineOffset(10, 10)
                }
                for( idx <- artifacts ){
                  svg.path { 
                      _.stroke(pipeStroke)
                       .strokeWidth(pipeWidth)
                       .move(
                          artifactX(idx)+artifactWidth/2, 
                          lastUsed(idx)
                        )
                       .lineY(offsetY+bottom+20)                
                  }
                  g.rect(artifactX(idx)+artifactWidth/2-10, 20, 20, 20)
                  lastUsed(idx) = offsetY+bottom+40
                }
              }
            }

            bottom += 40

            if(!newOutputs.isEmpty){
              val artifacts = 
                newOutputs.keys.toSeq.map { k => 
                  k -> scope(k)
                }.toMap.mapValues { _._1 }

              g.g { g => 
                g.translate(0, bottom)
                g.path { 
                  _.stroke(pipeStroke)
                   .strokeWidth(pipeWidth)
                   .move(150, -10)
                   .lineOffset(15, 15)
                   .lineX(artifactX(artifacts.values.max)+artifactWidth/2-15)
                }
                for( (name, idx) <- artifacts ){
                  g.rect(artifactX(idx), 20, artifactWidth, 30)
                  g.text(artifactX(idx)+10, 40, name)
                  g.path { 
                    _.stroke(pipeStroke)
                     .strokeWidth(pipeWidth)
                     .move(artifactX(idx)+artifactWidth/2-15, 5)
                     .lineOffset(15, 15)
                  }
                  lastUsed(idx) = offsetY+bottom+50
                }
                bottom += 60
              }
            }

            for(idx <- passThroughs){
              svg.path { 
                  _.stroke(pipeStroke)
                   .strokeWidth(pipeWidth)
                   .move(
                      artifactX(idx)+artifactWidth/2, 
                      lastUsed(idx)
                    )
                   .lineY(offsetY+bottom)                
              }
            }


          }

          offsetY += bottom
        }
      }

      svg.width = artifactX(scope.size)
      svg.height = offsetY

      return svg.render()
    }
  }
}

