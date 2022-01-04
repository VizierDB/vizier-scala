package info.vizierdb.ui.network

/* this file is AUTOGENERATED by `scripts/build_routes` from `vizier/resources/vizier-routes.txt` */
/* DO NOT EDIT THIS FILE DIRECTLY */

import scala.scalajs.js
import play.api.libs.json._
import org.scalajs.dom.ext.Ajax

import info.vizierdb.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import info.vizierdb.serialized
import info.vizierdb.ui.components.Parameter
import info.vizierdb.util.Logging
import info.vizierdb.serializers._
import info.vizierdb.spark.caveats.DataContainer
import info.vizierdb.nativeTypes.Caveat

case class API(baseUrl: String)
  extends Object
  with Logging
  with APIExtras
{

  def makeUrl(path: String, query: (String, Option[String])*): String = 
    baseUrl + path + (
      if(query.isEmpty) { "" }
      else{ "?" + query.collect { case (k, Some(v)) => k + "=" + v }.mkString("&") }
    )

  /** GET / **/
  def serviceDescriptor(
  ):Future[serialized.ServiceDescriptor] =
  {
    val url = makeUrl(s"/")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ServiceDescriptor]
    }
  }

  /** GET /projects **/
  def projectList(
  ):Future[serialized.ProjectList] =
  {
    val url = makeUrl(s"/projects")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ProjectList]
    }
  }

  /** POST /projects **/
  def projectCreate(
    properties:serialized.PropertyList.T,
  ):Future[serialized.ProjectSummary] =
  {
    val url = makeUrl(s"/projects")
    Ajax.post(
      url = url,
      data = Json.obj(
        "properties" -> properties,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ProjectSummary]
    }
  }

  /** GET /projects/{projectId:long} **/
  def projectGet(
    projectId:Long,
  ):Future[serialized.ProjectDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ProjectDescription]
    }
  }

  /** POST /projects/{projectId:long} **/
  def projectReplaceProps(
    projectId:Long,
    properties:serialized.PropertyList.T,
    defaultBranch:Option[Identifier] = None,
  ):Future[serialized.ProjectSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}")
    Ajax.post(
      url = url,
      data = Json.obj(
        "properties" -> properties,
        "defaultBranch" -> defaultBranch,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ProjectSummary]
    }
  }

  /** DELETE /projects/{projectId:long} **/
  def projectDelete(
    projectId:Long,
  ):Unit =
  {
    val url = makeUrl(s"/projects/${projectId}")
    Ajax.delete(
      url = url,
    )
  }

  /** PUT /projects/{projectId:long} **/
  def projectUpdate(
    projectId:Long,
    properties:serialized.PropertyList.T,
    defaultBranch:Option[Identifier] = None,
  ):Future[serialized.ProjectSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}")
    Ajax.put(
      url = url,
      data = Json.obj(
        "properties" -> properties,
        "defaultBranch" -> defaultBranch,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ProjectSummary]
    }
  }

  /** GET /projects/{projectId:long}/branches **/
  def branchList(
    projectId:Long,
  ):Future[serialized.BranchList] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.BranchList]
    }
  }

  /** POST /projects/{projectId:long}/branches **/
  def branchCreate(
    projectId:Long,
    source:Option[serialized.BranchSource] = None,
    properties:serialized.PropertyList.T,
  ):Future[serialized.BranchSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches")
    Ajax.post(
      url = url,
      data = Json.obj(
        "source" -> source,
        "properties" -> properties,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.BranchSummary]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long} **/
  def branchGet(
    projectId:Long,
    branchId:Long,
  ):Future[serialized.BranchDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.BranchDescription]
    }
  }

  /** DELETE /projects/{projectId:long}/branches/{branchId:long} **/
  def branchDelete(
    projectId:Long,
    branchId:Long,
  ):Unit =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}")
    Ajax.delete(
      url = url,
    )
  }

  /** PUT /projects/{projectId:long}/branches/{branchId:long} **/
  def branchUpdate(
    projectId:Long,
    branchId:Long,
    properties:serialized.PropertyList.T,
  ):Future[serialized.BranchSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}")
    Ajax.put(
      url = url,
      data = Json.obj(
        "properties" -> properties,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.BranchSummary]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/cancel **/
  def workflowBranchCancel(
    projectId:Long,
    branchId:Long,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/cancel")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long} **/
  def workflowGet(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/head **/
  def workflowHeadGet(
    projectId:Long,
    branchId:Long,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/cancel **/
  def workflowCancel(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/cancel")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/cancel **/
  def workflowHeadCancel(
    projectId:Long,
    branchId:Long,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/cancel")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/sql?query:string **/
  def workflowQuery(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    query:Option[String] = None,
  ):Future[DataContainer] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/sql",
                      "query" -> query.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[DataContainer]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/head/sql?query:string **/
  def workflowHeadQuery(
    projectId:Long,
    branchId:Long,
    query:Option[String] = None,
  ):Future[DataContainer] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/sql",
                      "query" -> query.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[DataContainer]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules **/
  def workflowModules(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
  ):Future[Seq[serialized.ModuleDescription]] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[Seq[serialized.ModuleDescription]]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/head/modules **/
  def workflowHeadModules(
    projectId:Long,
    branchId:Long,
  ):Future[Seq[serialized.ModuleDescription]] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[Seq[serialized.ModuleDescription]]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules **/
  def workflowAppend(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules")
    Ajax.post(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules **/
  def workflowHeadAppend(
    projectId:Long,
    branchId:Long,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules")
    Ajax.post(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int} **/
  def workflowGetModule(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.ModuleDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ModuleDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int} **/
  def workflowHeadGetModule(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.ModuleDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ModuleDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int} **/
  def workflowInsert(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}")
    Ajax.post(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int} **/
  def workflowHeadInsert(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}")
    Ajax.post(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** DELETE /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int} **/
  def workflowDelete(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}")
    Ajax.delete(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** DELETE /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int} **/
  def workflowHeadDelete(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}")
    Ajax.delete(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** PUT /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int} **/
  def workflowReplace(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}")
    Ajax.put(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** PUT /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int} **/
  def workflowHeadReplace(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
    packageId:String,
    commandId:String,
    arguments:serialized.CommandArgumentList.T,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}")
    Ajax.put(
      url = url,
      data = Json.obj(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> arguments,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int}/thaw **/
  def workflowThawUpto(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}/thaw")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int}/thaw **/
  def workflowHeadThawUpto(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}/thaw")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int}/thaw_one **/
  def workflowThawOne(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}/thaw_one")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int}/thaw_one **/
  def workflowHeadThawOne(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}/thaw_one")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int}/freeze **/
  def workflowFreezeFrom(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}/freeze")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int}/freeze **/
  def workflowHeadFreezeFrom(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}/freeze")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int}/freeze_one **/
  def workflowFreezeOne(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}/freeze_one")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** POST /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int}/freeze_one **/
  def workflowHeadFreezeOne(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
  ):Future[serialized.WorkflowDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}/freeze_one")
    Ajax.post(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.WorkflowDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/workflows/{workflowId:long}/modules/{modulePosition:int}/charts/{artifactId:int} **/
  def artifactGetChart(
    projectId:Long,
    branchId:Long,
    workflowId:Long,
    modulePosition:Int,
    artifactId:Int,
  ):Future[serialized.ArtifactDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/workflows/${workflowId}/modules/${modulePosition}/charts/${artifactId}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactDescription]
    }
  }

  /** GET /projects/{projectId:long}/branches/{branchId:long}/head/modules/{modulePosition:int}/charts/{artifactId:int} **/
  def artifactHeadGetChart(
    projectId:Long,
    branchId:Long,
    modulePosition:Int,
    artifactId:Int,
  ):Future[serialized.ArtifactDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/branches/${branchId}/head/modules/${modulePosition}/charts/${artifactId}")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactDescription]
    }
  }

  /** POST /projects/{projectId:long}/datasets **/
  def artifactCreateDataset(
    projectId:Long,
    columns:Seq[serialized.DatasetColumn],
    rows:Seq[serialized.DatasetRow],
    name:Option[String] = None,
    properties:serialized.PropertyList.T,
    annotations:Option[serialized.DatasetAnnotation] = None,
  ):Future[serialized.ArtifactSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}/datasets")
    Ajax.post(
      url = url,
      data = Json.obj(
        "columns" -> columns,
        "rows" -> rows,
        "name" -> name,
        "properties" -> properties,
        "annotations" -> annotations,
      ).toString
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactSummary]
    }
  }

  /** GET /projects/{projectId:long}/datasets/{artifactId:long}?offset:long&limit:int&profile:string **/
  def artifactGetDataset(
    projectId:Long,
    artifactId:Long,
    offset:Option[Long] = None,
    limit:Option[Int] = None,
    profile:Option[String] = None,
  ):Future[serialized.DatasetDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/datasets/${artifactId}",
                      "offset" -> offset.map { _.toString },
                      "limit" -> limit.map { _.toString },
                      "profile" -> profile.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.DatasetDescription]
    }
  }

  /** GET /projects/{projectId:long}/datasets/{artifactId:long}/annotations?column:int&row:string **/
  def artifactDsGetAnnotations(
    projectId:Long,
    artifactId:Long,
    column:Option[Int] = None,
    row:Option[String] = None,
  ):Future[Seq[Caveat]] =
  {
    val url = makeUrl(s"/projects/${projectId}/datasets/${artifactId}/annotations",
                      "column" -> column.map { _.toString },
                      "row" -> row.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[Seq[Caveat]]
    }
  }

  /** GET /projects/{projectId:long}/datasets/{artifactId:long}/descriptor **/
  def artifactDsGetSummary(
    projectId:Long,
    artifactId:Long,
  ):Future[serialized.ArtifactSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}/datasets/${artifactId}/descriptor")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactSummary]
    }
  }

  /** GET /projects/{projectId:long}/artifacts/{artifactId:long}?offset:long&limit:int&profile:string **/
  def artifactGet(
    projectId:Long,
    artifactId:Long,
    offset:Option[Long] = None,
    limit:Option[Int] = None,
    profile:Option[String] = None,
  ):Future[serialized.ArtifactDescription] =
  {
    val url = makeUrl(s"/projects/${projectId}/artifacts/${artifactId}",
                      "offset" -> offset.map { _.toString },
                      "limit" -> limit.map { _.toString },
                      "profile" -> profile.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactDescription]
    }
  }

  /** GET /projects/{projectId:long}/artifacts/{artifactId:long}/annotations?column:int&row:string **/
  def artifactGetAnnotations(
    projectId:Long,
    artifactId:Long,
    column:Option[Int] = None,
    row:Option[String] = None,
  ):Future[Seq[Caveat]] =
  {
    val url = makeUrl(s"/projects/${projectId}/artifacts/${artifactId}/annotations",
                      "column" -> column.map { _.toString },
                      "row" -> row.map { _.toString },
                     )
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[Seq[Caveat]]
    }
  }

  /** GET /projects/{projectId:long}/artifacts/{artifactId:long}/descriptor **/
  def artifactGetSummary(
    projectId:Long,
    artifactId:Long,
  ):Future[serialized.ArtifactSummary] =
  {
    val url = makeUrl(s"/projects/${projectId}/artifacts/${artifactId}/descriptor")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[serialized.ArtifactSummary]
    }
  }

  /** GET /tasks **/
  def taskList(
  ):Future[Seq[serialized.WorkflowSummary]] =
  {
    val url = makeUrl(s"/tasks")
    Ajax.get(
      url = url,
    ).map { xhr => 
      Json.parse(xhr.responseText)
          .as[Seq[serialized.WorkflowSummary]]
    }
  }

  /** POST /reload **/
  def serviceReload(
  ):Unit =
  {
    val url = makeUrl(s"/reload")
    Ajax.post(
      url = url,
    )
  }

}
