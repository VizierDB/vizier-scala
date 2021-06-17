package info.vizierdb.ui

import scala.scalajs.js
import scala.scalajs.js.JSON
import org.scalajs.dom.ext.Ajax

import info.vizierdb.VizierURLs
import info.vizierdb.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import state.{
  ProjectDescription,
  BranchDescription,
}

case class API(baseUrl: String)
{
  val urls = new VizierURLs(baseUrl)

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