package info.vizierdb.ui

import scala.scalajs.js
import scala.scalajs.js.JSON
import org.scalajs.dom.ext.Ajax

import info.vizierdb.VizierURLs
import info.vizierdb.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import info.vizierdb.ui.network.{
  ProjectDescription,
  BranchDescription,
}
import info.vizierdb.ui.network.PackageDescriptor
import info.vizierdb.ui.network.ServiceDescriptor
import info.vizierdb.ui.components.Parameter

case class API(baseUrl: String)
{
  val urls = new VizierURLs(baseUrl)

  def packages(): Future[Seq[PackageDescriptor]] =
  {
    Ajax.get(
      urls.serviceDescriptor.toString
    ).map { xhr => 
      JSON.parse(
        xhr.responseText
      ).asInstanceOf[ServiceDescriptor]
       .environment
       .packages
       .toSeq
    }.recover { 
      case error => 
        println(error.toString)
        Seq.empty 
      }
  }

  def project(
    projectId: Identifier
  ): Future[ProjectDescription] =
  {
    Ajax.get(
      urls.getProject(projectId).toString
    ).map { xhr =>
      val response = JSON.parse(
        xhr.responseText
      )
      if(response.id.equals(js.undefined)){
        if(response.message.equals(js.undefined)){
          throw new Exception("Unexpected error fetching project")
        } else {
          throw new Exception(s"While fetching project: ${response.message}")
        }
      }
      response
    }.map {
      _.asInstanceOf[ProjectDescription]
    }
  }

  def branch(
    projectId: Identifier, 
    branchId: Identifier
  ): Future[BranchDescription] =
  {
    Ajax.get(
      urls.getBranch(projectId, branchId).toString
    ).map { xhr =>
      JSON.parse(
        xhr.responseText
      )
    }.map {
      _.asInstanceOf[BranchDescription]
    }
  }


}