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
package info.vizierdb.api.spreadsheet

import scalikejdbc._
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._
import info.vizierdb.spreadsheet.Spreadsheet
import info.vizierdb.catalog.Artifact
import info.vizierdb.types._
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import info.vizierdb.spreadsheet.SpreadsheetCallbacks
import info.vizierdb.serialized.DatasetColumn
import org.apache.spark.util.collection.ErrorMessage
import info.vizierdb.catalog.CatalogDB
import scala.concurrent.ExecutionContext
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher
import spire.implicits
import info.vizierdb.catalog.Project
import info.vizierdb.catalog.Branch
import info.vizierdb.commands.Arguments
import info.vizierdb.commands.data.SpreadsheetCommand
import info.vizierdb.spreadsheet.EncodedSpreadsheet
import info.vizierdb.catalog.Workflow
import info.vizierdb.viztrails.Scheduler

class SpreadsheetSocket(client: String)(implicit val ec: ExecutionContext, system: ActorSystem, mat: Materializer)
  extends SpreadsheetCallbacks
  with LazyLogging
{
  var spreadsheet: Spreadsheet = null
  var inputDataset: Identifier = -1

  logger.debug(s"[$client] Websocket opened")
  
  val (remote, publisher): (ActorRef, Publisher[TextMessage.Strict]) =
        Source.actorRef[String](100000, OverflowStrategy.fail)
          .map(msg => TextMessage.Strict(msg))
          .toMat(Sink.asPublisher(false))(Keep.both)
          .run()
    
  val sink: Sink[Message, Any] = Flow[Message]
      .map {
        case TextMessage.Strict(msg) =>
          onText(msg)
        case _ => 
          logger.error(s"[$client] Unexpected websocket message")
      }
      .to(Sink.onComplete { msg => {
        logger.debug(s"[$client] Websocket closed: $msg")
      }})

  val flow = 
    Flow.fromSinkAndSource(sink, Source.fromPublisher(publisher))

  def onText(data: String): Unit =
  {
    logger.trace(s"[$client] Websocket received ${data.length()} bytes: ${data.take(20)}")

    try {
      Json.parse(data).as[SpreadsheetRequest] match {

        //////
        // Open the spreadsheet sourced on an empty dataset
        //////
        case OpenEmpty() =>
        {
          if(spreadsheet != null){ send(ReportError("Spreadsheet already opened", "")); return }
          logger.trace(s"Opening empty spreadsheet")

          initWithSpreadsheet { Spreadsheet() }
        }

        //////
        // Open the spreadsheet over a specific dataset artifact
        //////
        case OpenDataset(projectId, datasetId) => 
        {
          if(spreadsheet != null){ send(ReportError("Spreadsheet already opened", "")); return }
          logger.trace(s"Opening spreadsheet on dataset $projectId:$datasetId")
          initWithSpreadsheet {
            Spreadsheet(
              CatalogDB.withDB { implicit s => 
                Artifact.get(target = datasetId, projectId = Some(projectId))
                  .dataframe
              }()
            )
          }
        }

        //////
        // Open the spreadsheet, resuming an editing session persisted
        // in a notebook cell
        //////
        case OpenWorkflowCell(projectId, branchId, moduleId) =>
        {
          if(spreadsheet != null){ send(ReportError("Spreadsheet already opened", "")); return }
          logger.trace(s"Opening cell: $projectId/$branchId/head/$moduleId")
          initWithSpreadsheet {
            val (cell, module, args, source) =
              CatalogDB.withDBReadOnly { implicit s =>
                val (cell, module) =
                  Branch.get(projectId, branchId)
                        .head
                        .cellAndModuleByModuleId(moduleId)
                        .getOrElse {
                          send(ReportError(s"Invalid cell: $projectId/$branchId/head/$moduleId", ""))
                          return
                        }
                  if(  (module.packageId != "data")
                    || (module.commandId != "spreadsheet"))
                  {
                    send(ReportError(s"Cell is not a spreadsheet: $projectId/$branchId/head/$moduleId (${module.packageId}.${module.commandId}", ""))
                    return
                  }
                  val args = Arguments(module.arguments, SpreadsheetCommand.parameters)
                  val input = args.getOpt[String](SpreadsheetCommand.PARAM_INPUT)
                  (
                    cell,
                    module,
                    args,
                    input.map { artifact => 
                      val artifacts = cell.inputs
                        artifacts
                          .find { _.userFacingName == artifact }
                          .flatMap { a => 
                            a.artifactId.map {
                              a.userFacingName -> _
                            } 
                          }
                          .getOrElse {
                            send(ReportError(s"Invalid source dataset: $artifact (out of ${artifacts.map { _.userFacingName }.mkString(", ")}) ", ""))

                            return
                          }
                    }
                  )
              }
            args.getOpt[JsValue](SpreadsheetCommand.PARAM_SPREADSHEET) match {
              case None | Some(JsNull) => 
                source match {
                  case Some( (_, datasetId) ) =>
                    Spreadsheet(
                      CatalogDB.withDB { implicit s => 
                        Artifact.get(target = datasetId, projectId = Some(projectId))
                          .dataframe
                      }()
                    )
                  case None => 
                    Spreadsheet()
                }
              case Some(encoded) =>
                val parsed = encoded.as[EncodedSpreadsheet]
                source match {
                  case Some( (_, datasetId) ) =>
                    parsed.rebuildFromDataframe(
                      CatalogDB.withDB { implicit s => 
                        Artifact.get(target = datasetId, projectId = Some(projectId))
                          .dataframe
                      }()
                    )
                  case None =>
                    parsed.rebuildFromEmpty
                }
            }
          }
        }

        case SaveWorkflowCell(projectId, branchId, moduleId, input, output) =>
        {
          val serialized = EncodedSpreadsheet.fromSpreadsheet(spreadsheet)
          val (workflow, workflowIdToAbort): (Workflow, Option[Identifier]) = 
            CatalogDB.withDB { implicit s =>
              val branch = Branch.get(projectId, branchId)
              val currentWorkflow = branch.head
              val updatedWorkflow =
                branch.updateById(moduleId, "data", "spreadsheet")(
                  SpreadsheetCommand.PARAM_INPUT -> input.map { JsString(_) }.getOrElse { JsNull },
                  SpreadsheetCommand.PARAM_OUTPUT -> output.map { JsString(_) }.getOrElse { JsNull },
                  SpreadsheetCommand.PARAM_SPREADSHEET -> Json.toJson(serialized),
                )._2
              (
                updatedWorkflow,
                if(currentWorkflow.isRunning){ Some(currentWorkflow.id) } else { None }
              )
            }
          logger.trace(s"Scheduling ${workflow.id}")
          // The workflow must be scheduled AFTER the enclosing transaction finishes
          if(workflowIdToAbort.isDefined) {
            Scheduler.abort(workflowIdToAbort.get)
          }
          Scheduler.schedule(workflow)
        }

        //////
        // Register a new set of rows as being "of interest"
        //////
        case SubscribeRows(row, count) => 
        {
          spreadsheet.subscribe(row, count)
          refreshRows(row, count)
        }

        //////
        // Indicate a lack of interest in a set of rows
        //////
        case UnsubscribeRows(row, count) => 
        {
          spreadsheet.unsubscribe(row, count)
        }

        //////
        // Update the contents of a cell
        //////
        case EditCell(column, row, v) => 
        {
          spreadsheet.editCell(column, row, v)
        }
        case Ping(id) => send(Pong(id))
      }
    } catch {
      case e: Throwable => 
        e.printStackTrace()
        send(ReportError(e))
    }
  }

  def initWithSpreadsheet(constructor: => Spreadsheet): Unit =
  {
    Future { 
      logger.debug("Initializing spreadsheet!")
      spreadsheet = constructor 
      spreadsheet.callbacks += this
      logger.debug("Spreadsheet initialized!")
    }
      .onComplete {
        case Success(_) => send(Connected("Generic Dataset")); refreshEverything()
        case Failure(err) => 
          err.printStackTrace()
          send(ReportError(err))
      }
  }

  /**
   * Send a message
   */
  def send(message: SpreadsheetResponse) =
  {
    synchronized {
      logger.trace(s"SEND: ${message.toString.take(200)}")
      remote ! Json.stringify(Json.toJson(message))
    } 
  }
  
  def refreshEverything(): Unit = 
  {
    logger.trace("Refreshing Everything")
    sizeChanged(spreadsheet.size)
    refreshHeaders()
    refreshData()
  }
  
  def refreshHeaders(): Unit =   
    send(UpdateSchema(spreadsheet.schema.map { case output =>
      DatasetColumn(output.id, output.output.name, output.output.dataType)
    }))
  
  def refreshData(): Unit = 
  {
    val t = spreadsheet.schema.map { _.output.dataType }
    val sizeBound = spreadsheet.size
    for( (low, high) <- spreadsheet.subscriptions ){
      if(low < sizeBound){
        val boundHigh = math.min(sizeBound-1, high)
        logger.trace(s"Refreshing [$low,$boundHigh]")
        send(DeliverRows(low, 
          (low to boundHigh).map { rowIndex => 
            spreadsheet.getRow(rowIndex)
                       .zip(t)
                       .map { case (v, t) => SpreadsheetCell(v, t) }
                       .toArray[SpreadsheetCell]
          }.toArray
        ))
      }
    }
  }  

  def refreshRows(from: Long, count: Long): Unit = 
  {
    val t = spreadsheet.schema.map { _.output.dataType }
    val cappedUntil = math.min(spreadsheet.size, from+count)
    logger.trace(s"Refreshing $from+$count -> [$from,$cappedUntil]")
    send(DeliverRows(from, 
      (from until cappedUntil).map { rowIndex => 
        spreadsheet.getRow(rowIndex)
                   .zip(t)
                   .map { case (v, t) => SpreadsheetCell(v, t) }
                   .toArray[SpreadsheetCell]
      }.toArray
    ))
  }

  def refreshCell(column: Int, row: Long): Unit = 
  {
    assert(row >= 0 && row < spreadsheet.size, s"The row $row doesn't exist")
    logger.trace(s"Cell invalidated: $column:$row")
    // These can come in bursts, so make notification delivery asynchronous
    Future {
      try {
        logger.trace(s"Constructing invalidation message for $column:$row")
        val message = DeliverCell(column, row, SpreadsheetCell(spreadsheet.getCell(column, row, notify = true), spreadsheet.schema(column).output.dataType))
        logger.trace(s"Sending invalidation message $message")
        send(message)
        logger.trace(s"sent invalidation message")
      } catch {
        case t: Throwable => 
          logger.error(s"Error refreshing cell $column:$row: ${t.getMessage()}")
          t.printStackTrace()
      }
    }
  }

  def sizeChanged(newSize: Long): Unit =
  {
    send(UpdateSize(newSize))
  }

}

object SpreadsheetSocket
{
  def monitor(client: String)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer): Flow[Message, Message, Any] =
    new SpreadsheetSocket(client).flow
}