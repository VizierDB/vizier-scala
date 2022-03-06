package info.vizierdb.commands.mimir.facets

import play.api.libs.json._
import org.apache.spark.sql.DataFrame

trait Facet
{
  def identity: String
  def description: String
  def test(query:DataFrame): Seq[String]
  def toJson: JsValue

  def affectsColumn: Option[String]
}

object Facet
{
  val detectors: Map[String, FacetDetector] =
    Seq[FacetDetector](
      ExpectedColumns,
      ExpectedType,
      Nullable,
      ExpectedValues
    ).map { x => x.identity -> x }.toMap

  def detect(query: DataFrame): Seq[Facet] =
    detectors.values.map { _(query) }.flatten.toSeq

  def encode(facet: Facet): JsValue =
    Json.obj(
      "type"    -> facet.identity,
      "content" -> facet.toJson
    )

  def decode(encoded: JsValue): Facet =
    detectors.get( (encoded \ "type").as[String] )
             .map { _.decode( (encoded \ "content").as[JsValue] ) }
             .getOrElse { throw new IllegalArgumentException(s"Unsupported facet type $encoded") }


  implicit val format: Format[Facet] = Format(
    new Reads[Facet] { def reads(j: JsValue) = JsSuccess(decode(j)) },
    new Writes[Facet] { def writes(j: Facet) = encode(j) }
  )
}