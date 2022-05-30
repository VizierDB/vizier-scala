package info.vizierdb.api.websocket

/* this file is AUTOGENERATED by `scripts/build_routes` from `vizier/resources/vizier-routes.txt` */
/* DO NOT EDIT THIS FILE DIRECTLY */

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.api.handler._
import info.vizierdb.api._
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.spark.caveats.DataContainer

abstract class BranchWatcherAPIRoutes
{
  implicit def liftToOption[T](x: T): Option[T] = Some(x)
  def projectId: Identifier
  def branchId: Identifier

  def route(path: Seq[String], args: Map[String, JsValue]): JsValue =
    path.last match {
      case "workflowGet" => Json.toJson(GetWorkflow(branchId = branchId, projectId = projectId):serialized.WorkflowDescription)
      case "workflowCancel" => Json.toJson(CancelWorkflow(branchId = branchId, projectId = projectId):serialized.WorkflowDescription)
      case "workflowQuery" => Json.toJson(WorkflowSQL(branchId = branchId, projectId = projectId, query = args("query").as[String]):DataContainer)
      case "workflowModules" => Json.toJson(GetAllModules(branchId = branchId, projectId = projectId):Seq[serialized.ModuleDescription])
      case "workflowAppend" => Json.toJson(AppendModule(branchId = branchId, projectId = projectId, packageId = args("packageId").as[String], commandId = args("commandId").as[String], arguments = args("arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription)
      case "workflowSuggest" => Json.toJson(SuggestCommand(branchId = branchId, projectId = projectId, before = args("before").as[Long], after = args("after").as[Long]):Seq[serialized.PackageDescription])
      case "workflowGetModule" => Json.toJson(GetModule(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.ModuleDescription)
      case "workflowInsert" => Json.toJson(InsertModule(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int], packageId = args("packageId").as[String], commandId = args("commandId").as[String], arguments = args("arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription)
      case "workflowDelete" => Json.toJson(DeleteModule(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.WorkflowDescription)
      case "workflowReplace" => Json.toJson(ReplaceModule(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int], packageId = args("packageId").as[String], commandId = args("commandId").as[String], arguments = args("arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription)
      case "workflowThawUpto" => Json.toJson(ThawModules(thawUptoHere=true)(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.WorkflowDescription)
      case "workflowThawOne" => Json.toJson(ThawModules(thawUptoHere=false)(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.WorkflowDescription)
      case "workflowFreezeFrom" => Json.toJson(FreezeModules(freezeFromHere=true)(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.WorkflowDescription)
      case "workflowFreezeOne" => Json.toJson(FreezeModules(freezeFromHere=false)(branchId = branchId, projectId = projectId, modulePosition = args("modulePosition").as[Int]):serialized.WorkflowDescription)
    }
}
