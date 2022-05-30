package info.vizierdb.gis

import org.locationtech.jts.geom.{ Geometry, Envelope, Polygon, Point }
import play.api.libs.json._

object GeoJson
{
  implicit val geometryFormat = Format[Geometry](
    new Reads[Geometry] { 
      def reads(j: JsValue): JsResult[Geometry] = ???
    },
    new Writes[Geometry] {
      def writes(geometry: Geometry): JsValue =
        geometry match {
          case point:Point => 
            Json.obj(
              "type" -> "Point",
              "coordinates" -> Json.arr(point.getX, point.getY)
            )
          case polygon:Polygon =>
            val rings = (
              polygon.getExteriorRing() +:
              (0 until polygon.getNumInteriorRing())
                .map { polygon.getInteriorRingN(_) }
            )
            Json.obj(
              "type" -> "Polygon",
              "coordinates" -> JsArray(
                rings.map { ring =>
                  JsArray(ring.getCoordinates.map { coord =>
                     Json.arr(coord.getX, coord.getY)
                  })
                }
              )
            )

        }
    }
  )
}
import GeoJson.geometryFormat

case class FeatureCollection(
  features: Seq[Feature],
  `type`: String = "FeatureCollection"
)

object FeatureCollection
{
  implicit val format: Format[FeatureCollection] = Json.format
  def apply(geometry: Iterable[Geometry]): FeatureCollection =
    FeatureCollection(geometry.map { Feature(_) }.toSeq)
}

case class Feature(
  geometry: Geometry,
  properties: JsObject = Json.obj(),
  `type`: String = "Feature"
)

object Feature
{

  implicit val format: Format[Feature] = Json.format
}
