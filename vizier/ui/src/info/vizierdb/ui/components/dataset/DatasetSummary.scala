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
                        div(s"Mean: $mean"),
                        div(s"Min: $min"),
                        div(s"Max: $max")
                      )
                    case _ => div() // No additional stats for non-numeric types
                  }
                  
                  div(
                    div(s"Column: $columnName"),
                    div(s"Type: $columnType"),
                    div(s"Column Information:",
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
}

