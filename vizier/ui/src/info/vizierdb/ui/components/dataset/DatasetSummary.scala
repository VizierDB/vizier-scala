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
  
  def fetchAndRenderDatasetInfo(): Future[HtmlTag] = {
    Vizier.api.artifactGet(
      projectId,
      datasetId,
      limit = Some(0),
      profile = Some("true")
    ).map {
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
                        div(`class` := "statistics_detail",s"Mean: $mean"),
                        div(`class` := "statistics_detail",s"Min: $min"),
                        div(`class` := "statistics_detail",s"Max: $max")
                      )
                    case _ => div() // No additional stats for non-numeric types
                  }
                    div(`class` := "column_detail",
                    div(`class` := "column_name", div(s"$columnName"), div(s"($columnType)")),
                    div(`class` := "statistics",
                        div(`class` := "statistics_detail",s"Distinct Values: $distinctValueCount"),
                        div(`class` := "statistics_detail",s"Null Count: $nullCount"),
                        div(`class` := "statistics_detail",s"Count: $count"),
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
      val dsSummaryBody = root.querySelector(".dataset_summary_body")
      dsSummaryBody.innerHTML = ""
      dsSummaryBody.appendChild(dsSummary.render) 

    case Failure(exception) =>
      root.innerHTML = s"Error loading dataset summary: ${exception.getMessage}" // Update root directly
  }
  }

  val root: dom.html.Div = div(`class` := "dataset_summary",
                                div(`class` := "dataset_summary_header",
                                div(`class` := "column_name_header", b(s"Column Name")),
                                div(`class` := "statistics_header", b(s"Statistics"))
                                ),
                                div(`class` := "dataset_summary_body")
                              ).render
val headersHtml = 

  updateSummary()
}

