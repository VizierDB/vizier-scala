package info.vizierdb.ui

import scala.scalajs.js
import play.api.libs.json._
import org.scalajs.dom.ext.Ajax

import info.vizierdb.VizierURLs
import info.vizierdb.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import info.vizierdb.serialized
import info.vizierdb.ui.components.Parameter
import info.vizierdb.util.Logging
import info.vizierdb.serializers._

case class API(baseUrl: String)
  extends Object
  with Logging
{
  val urls = new VizierURLs(baseUrl)

  def packages(): Future[Seq[serialized.PackageDescription]] =
  {
    Ajax.get(
      urls.serviceDescriptor.toString
    ).map { xhr => 
      Json.parse(
        xhr.responseText
      ).as[serialized.ServiceDescriptor]
       .environment
       .packages
       .toSeq
    }.recover { 
      case error => 
        logger.error(error.toString)
        Seq.empty 
      }
  }

  def listProjects(): Future[serialized.ProjectList] =
  {
    Ajax.get(
      urls.listProjects.toString
    ).map { xhr =>
      Json.parse(
        xhr.responseText
      ).as[serialized.ProjectList]
    }
  }

  def createProject(
    properties: serialized.PropertyList.T
  ): Future[serialized.ProjectSummary] =
  {
    Ajax.post(
      urls.createProject.toString,
      data = Json.obj(
        "properties" -> properties
      ).toString
    ).map { xhr => 
      Json.parse(
        xhr.responseText
      ).as[serialized.ProjectSummary]
    }
  }


  def project(
    projectId: Identifier
  ): Future[serialized.ProjectDescription] =
  {
    Ajax.get(
      urls.getProject(projectId).toString
    ).map { xhr =>
      Json.parse(
        xhr.responseText
      ).as[serialized.ProjectDescription]
    }
  }

  def branch(
    projectId: Identifier, 
    branchId: Identifier
  ): Future[serialized.BranchDescription] =
  {
    Ajax.get(
      urls.getBranch(projectId, branchId).toString
    ).map { xhr =>
      Json.parse(
        xhr.responseText
      ).as[serialized.BranchDescription]
    }
  }

}