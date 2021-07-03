package info.vizierdb.ui

import scala.scalajs.js
import scala.scalajs.js.JSON
import org.scalajs.dom.ext.Ajax

import info.vizierdb.VizierURLs
import info.vizierdb.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import info.vizierdb.encoding
import info.vizierdb.ui.components.Parameter
import info.vizierdb.util.Logging

case class API(baseUrl: String)
  extends Object
  with Logging
{
  val urls = new VizierURLs(baseUrl)

  def packages(): Future[Seq[encoding.PackageDescriptor]] =
  {
    Ajax.get(
      urls.serviceDescriptor.toString
    ).map { xhr => 
      JSON.parse(
        xhr.responseText
      ).asInstanceOf[encoding.ServiceDescriptor]
       .environment
       .packages
       .toSeq
    }.recover { 
      case error => 
        logger.error(error.toString)
        Seq.empty 
      }
  }

  def project(
    projectId: Identifier
  ): Future[encoding.ProjectDescription] =
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
      _.asInstanceOf[encoding.ProjectDescription]
    }
  }

  def branch(
    projectId: Identifier, 
    branchId: Identifier
  ): Future[encoding.BranchDescription] =
  {
    Ajax.get(
      urls.getBranch(projectId, branchId).toString
    ).map { xhr =>
      JSON.parse(
        xhr.responseText
      )
    }.map {
      _.asInstanceOf[encoding.BranchDescription]
    }
  }


}