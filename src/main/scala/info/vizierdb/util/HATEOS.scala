package info.vizierdb.util

import play.api.libs.json._
import java.net.URL

object HATEOAS
{
  def apply(links: (String, URL)*): JsValue =
    Json.toJson(build(links))

  def build(links: Seq[(String, URL)]): Seq[Map[String, String]] = 
    links.filter { _._2 == null }
         .map { case (rel, href) => Map("rel" -> rel, "href" -> href.toString) }
         .toSeq

  def extend(base: JsValue, links: (String, URL)*): JsValue =
    JsArray(base.as[Seq[JsValue]] ++ build(links).map { Json.toJson(_) })

  val LINKS = "links"

  // General
  val SELF = "self"

  // API
  val API_DOC = "api.doc"
  val API_HOME = "api.doc"

  // Branch
  val BRANCH_CREATE = "branch.create"
  val BRANCH_DELETE = "branch.delete"
  val BRANCH_HEAD = "branch.head"
  val BRANCH_UPDATE = "branch.update"

  // Dataset
  val ANNOTATIONS_UPDATE = "annotations.update"
  val ANNOTATIONS_GET = "annotations.get"
  val DATASET_DOWNLOAD = "dataset.download"
  val DATASET_FETCH_ALL = "dataset.fetch"

  val PAGE_FIRST = "page.first"
  val PAGE_LAST = "page.last"
  val PAGE_NEXT = "page.next"
  val PAGE_PREV = "page.prev"

  // Files
  val FILE_DOWNLOAD = "file.download"
  val FILE_UPLOAD = "file.upload"

  // Modules
  val MODULE_DELETE = "module.delete"
  val MODULE_INSERT = "module.insert"
  val MODULE_REPLACE = "module.replace"

  // Projects
  val PROJECT_CREATE = "project.create"
  val PROJECT_IMPORT = "project.import"
  val PROJECT_LIST = "project.list"
  val PROJECT_DELETE = "project.delete"
  val PROJECT_UPDATE = "project.update"

  // Workflow
  val WORKFLOW_APPEND = "workflow.append"
  val WORKFLOW_BRANCH = "workflow.branch"
  val WORKFLOW_CANCEL = "workflow.cancel"
  val WORKFLOW_PROJECT = "workflow.project"
}
