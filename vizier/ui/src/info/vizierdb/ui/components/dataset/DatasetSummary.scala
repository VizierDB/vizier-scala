package info.vizierdb.ui.components.dataset

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scalajs.js
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.serialized.{ DatasetDescription, DatasetColumn, DatasetRow }
import info.vizierdb.nativeTypes.JsValue
import scala.concurrent.Future
import scala.concurrent.Promise
import info.vizierdb.ui.Vizier
import scala.util.{ Success, Failure }
import play.api.libs.json._


class DatasetSummary(projectId: Identifier, datasetId: Identifier)(implicit val owner: Ctx.Owner) {
  
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val artifact = Vizier.api.artifactGet(
      projectId,
      datasetId,
      limit = Some(0),
      profile = Some("true")
    )
  
  def fetchAndRenderDatasetInfo(): Future[HtmlTag] = {
    artifact.map {
      case ds: DatasetDescription =>
        ds.properties.find(_.key == "columns") match {
          case Some(property) =>
            property.value.asOpt[JsArray] match {
              case Some(columnsArray) =>
                val columnsHtml = columnsArray.value.map { columnJson =>
                  val columnName = (columnJson \ "column" \ "name").as[String]
                  val columnType = (columnJson \ "column" \ "type").as[String]
                  val distinctValueCount = (columnJson \ "distinctValueCount").asOpt[Int].getOrElse("N/A")
                  val nullCount = (columnJson \ "nullCount").asOpt[Int].getOrElse("N/A")
                  val count = (columnJson \ "count").asOpt[Int].getOrElse("N/A")
                  val statsHtml = columnType match {
                    case "integer" | "float" =>
                      val mean = (columnJson \ "mean").asOpt[Double].getOrElse("N/A")
                      val min = (columnJson \ "min").asOpt[Double].getOrElse("N/A")
                      val max = (columnJson \ "max").asOpt[Double].getOrElse("N/A")
                      div(
                        div(s"Mean: $mean"),
                        div(s"Min: $min"),
                        div(s"Max: $max")
                      )
                    case _ => div() // No additional stats for non-numeric types
                  }
                  
                  div(style := "display: flex; align-items: center; margin-bottom: 10px;",
                    div(style := "margin-right: 20px;", s"Column Name: $columnName"),
                    div(style := "margin-right: 20px;", s"Type: $columnType"),
                    div(style := "margin-right: 20px;",
                        div(s"Distinct Values: $distinctValueCount"),
                        div(s"Null Count: $nullCount"),
                        div(s"Count: $count"),
                        statsHtml),
                  )
                }
                div(columnsHtml: _*)
              case None => div("Columns data is not available.")
            }
          case None => div("Columns property not found.")
        }
      case _ =>
        div("Not a dataset or dataset information could not be retrieved.")
    }
  }
  
  def updateSummary(): Unit = {
  this.fetchAndRenderDatasetInfo().onComplete {
    case Success(dsSummary) =>
      root.innerHTML = ""
      root.appendChild(dsSummary.render) 

    case Failure(exception) =>
      root.innerHTML = s"Error loading dataset summary: ${exception.getMessage}" // Update root directly
  }
  }

  val root: dom.html.Div = div(`class` := "dataset_summary",s"").render


  updateSummary()
}

