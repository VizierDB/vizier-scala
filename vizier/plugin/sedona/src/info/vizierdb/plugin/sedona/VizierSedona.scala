package info.vizierdb.plugin.sedona

import org.apache.spark.sql.SparkSession
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.catalyst.FunctionIdentifier
import info.vizierdb.spark.SedonaPNGWrapper
import org.apache.sedona.spark.SedonaContext
import info.vizierdb.spark.SparkSchema
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import play.api.libs.json.JsString
import org.apache.sedona.sql.utils.GeometrySerializer
import org.apache.spark.sql.catalyst.util.ArrayData
import info.vizierdb.Plugin
import org.locationtech.jts.geom.Geometry
import org.apache.sedona.core.formatMapper.FormatMapper
import org.apache.sedona.common.enums.FileDataSplitter
import org.apache.sedona.common.raster.RasterOutputs
import org.apache.sedona.common.raster.RasterConstructors
import org.apache.sedona.common.raster.{ Serde => SedonaRasterSerde }
import org.apache.spark.sql.sedona_sql.UDT.RasterUDT
import java.util.Base64
import org.geotools.coverage.grid.GridCoverage2D
import info.vizierdb.commands.Commands
import info.vizierdb.Vizier

object VizierSedona 
  extends LazyLogging
{
  lazy val geometryFormatMapper = 
    new FormatMapper(FileDataSplitter.WKT, false)

  def base64Encode(b: Array[Byte]): String =
    Base64.getEncoder().encodeToString(b)

  def base64Decode(b: String): Array[Byte] =
    Base64.getDecoder().decode(b)

  def init(spark: SparkSession): Unit = 
  {
    // Sedona setup hooks
    SedonaContext.create(spark)
    System.setProperty("geospark.global.charset", "utf8")

    // Sedona UDTs
    Plugin.registerUDT("geometry", GeometryUDT,
      {
        case v: Geometry => JsString(v.toText)
        case v: ArrayData => JsString(GeometrySerializer.deserialize(v.toByteArray).toText),
      },
      {
        j => geometryFormatMapper.readGeometry(j.as[String])
      }
    )
    Plugin.registerUDT("raster", RasterUDT,
      {
        case k: GridCoverage2D => JsString(base64Encode(SedonaRasterSerde.serialize(k)))
      },
      {
        k => SedonaRasterSerde.deserialize(base64Decode(k.as[String]))
      }
    )

    // Sedona Cell Types
    //// TODO: Historically, most of these ops were part of the mimir package.  
    //// We should set up some sort of schema upgrade to migrate it
    //// over to e.g., the sedona package
    Commands("mimir").register("geotag" -> Geotag)
    
    val geocoders = Seq(
      Vizier.getProperty("google-api-key")
            .map { new geocoder.GoogleGeocoder(_) },
      Vizier.getProperty("osm-server")
            .map { new geocoder.OSMGeocoder(_) },
    ).flatten
    if(!geocoders.isEmpty){
      Commands("mimir").register("geocode" -> new geocoder.Geocode(
        geocoders = geocoders.map { x => x.name -> x }.toMap,
        cacheFormat = Vizier.getProperty("geocode-cache-format")
                            .getOrElse("parquet")
      ))
    }

    // Rejigger Sedona's AsPNG (if present) to dump out ImageUDT-typed data
    {
      val registry = 
        spark.sessionState
             .functionRegistry
      val as_png = FunctionIdentifier("RS_AsPNG")
      ( registry.lookupFunction(as_png),
        registry.lookupFunctionBuilder(as_png)
      ) match {
        case (Some(info), Some(builder)) =>
          registry.dropFunction(as_png)
          registry.registerFunction(as_png, info, 
            (args) => SedonaPNGWrapper(builder(args))
          )
        case (_,_) =>
          logger.warn("Can not override Sedona PNG class; Sedona's RS_AsPNG's output will not display properly in spreadsheets")
      }
    }
  }

  object plugin extends Plugin(
    name = "Sedona",
    schema_version = 1,
    plugin_class = VizierSedona.getClass.getName(),
    description = "Vizier support for Apache Sedona",
    documentation = Some("https://sedona.apache.org/1.5.0/")
  )
}