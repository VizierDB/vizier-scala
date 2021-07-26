/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.commands.plot

import play.api.libs.json._
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ Row, Column, DataFrame }
import org.apache.spark.sql.functions.{ expr, first }
import org.apache.spark.sql.catalyst.analysis.{ UnresolvedFunction, UnresolvedAttribute }
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.catalyst.expressions.{ Literal, Cast, Subtract }
import org.locationtech.jts.geom.{ Geometry, Envelope, Polygon, Point }
import org.apache.spark.sql.types.{ LongType, StructField }
import org.apache.sedona.viz.core.ImageSerializableWrapper
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import info.vizierdb.types.MIME
import java.util.Base64
import info.vizierdb.viztrails.ProvenancePrediction
import java.io.FileOutputStream
import info.vizierdb.gis._

object GeoPlot extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_LAYERS = "layers"
  val PARAM_FORMAT = "layer_type"
  val PARAM_NAME = "layer_name"
  val PARAM_SHAPE_COL = "shape_column"
  val PARAM_WEIGHT_COL = "weight_column"

  val POINT_LIMIT_THRESHOLD = 5000

  def name: String = "Plot Data on Map"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
    ListParameter(id = PARAM_LAYERS, name = "Layers", components = Seq(
      StringParameter(id = PARAM_NAME, name = "Layer Name", required = false),
      EnumerableParameter(id = PARAM_FORMAT, name = "Layer Type", values = EnumerableValue.withNames(
        "Vector (standard)" -> "vector",
        "Raster Heat Map" -> "heat_map",
      ), default = Some(0)),
      ColIdParameter(id = PARAM_SHAPE_COL, name = "Feature Column"),
      ColIdParameter(id = PARAM_WEIGHT_COL, name = "Weight", required = false),
    ))
  )
  def format(arguments: Arguments): String = 
    s"PLOT MAP DATA FROM ${arguments.pretty(PARAM_DATASET)}"
  def title(arguments: Arguments): String = 
    s"Plot ${arguments.pretty(PARAM_DATASET)} on map"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val df = context.dataframe(datasetName)
    var bounds: Envelope = null

    val layers = 
      arguments.getList(PARAM_LAYERS).zipWithIndex.map { case (layer, idx) =>
        val shape_column = df.schema.fields(layer.get[Int](PARAM_SHAPE_COL))
        val weight_column = layer.getOpt[Int](PARAM_WEIGHT_COL).map { df.schema.fields(_) }

        val boundsTable = df.agg(shape_column.name -> "st_envelope_aggr")
        val envelope = boundsTable.take(1).head.get(0).asInstanceOf[Geometry].getEnvelopeInternal()
        if(bounds == null){
          bounds = envelope
        } else {
          bounds.expandToInclude(envelope)
        }

        val features =
          FeatureCollection( 
            df.select(df(shape_column.name))
              .take(POINT_LIMIT_THRESHOLD + 1)
              .map { row => row.getAs[Geometry](0) }
          )

        val layerName = layer.getOpt[String](PARAM_NAME)
                             .getOrElse { shape_column.name.replaceAll("[^\"]", "") }

        val layerFile =
          context.outputFile(s"geojson_$idx", mimeType = MIME.JSON)
        val f = new FileOutputStream(layerFile.absoluteFile)
        f.write(Json.toJson(features).toString.getBytes)
        f.close

        s"""
        |{
        |  let layer = L.geoJson()
        |  layers["${layerName}"] = layer
        |  layer.addTo(theMap)
        |  
        |  let oReq = new XMLHttpRequest()
        |  oReq.onload = function(response){
        |    let data = JSON.parse(oReq.responseText)
        |    layer.addData(data)
        |  }
        |  oReq.open("GET", "${layerFile.url}")
        |  oReq.send()
        |}
        |""".stripMargin
      }

    if(bounds == null){
      context.error("No layers added")
      return
    }

    val mapDiv = "map_"+context.executionIdentifier
    val leafletVersion = "1.7.1"

    context.displayHTML(
      html = f"<div id='$mapDiv' style='height: 600px'/>",
      javascript = f"""
        |var theMap = L.map('$mapDiv')
        |              .fitBounds([
        |                 [${bounds.getMinY}, ${bounds.getMinX}],
        |                 [${bounds.getMaxY}, ${bounds.getMaxX}],
        |              ])
        |let defaultMapTiles = 
        |    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        |       attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        |    })
        |defaultMapTiles.addTo(theMap)
        |let baseLayers = {}
        |baseLayers["OpenStreetMap"] = defaultMapTiles
        |let layers = {}
        |${layers.mkString("\n")}
        |console.log(layers)
        |L.control.layers(baseLayers, layers).addTo(theMap)
        |""".stripMargin,
      javascriptDependencies = Seq(
        s"https://unpkg.com/leaflet@$leafletVersion/dist/leaflet-src.js"
      ),
      cssDependencies = Seq(
        s"https://unpkg.com/leaflet@$leafletVersion/dist/leaflet.css"
      )
    )
  } 

  def genRasterLayer(
    df: DataFrame, 
    shape_column: StructField, 
    weight_column: StructField, 
    bounds: Geometry
  ): String =
  {
    val pixelized = df.select(
        new Column(
          UnresolvedFunction("st_pixelize", Seq(
            /* Input attribute */
            UnresolvedAttribute(shape_column.name),
            /* width         x    height */
            Literal(256), /* x */ Literal(256),
            /* Bounds */
            Literal.fromObject(
              GeometryUDT.serialize(bounds)
            )
              
          ), false)
        ) as "pixel",
        df(weight_column.name) as "weight"
      )
      .groupBy("pixel")
      .agg( first("weight") as "weight" )

    val weightBoundsTable = 
      pixelized
        .select( pixelized("weight").cast(LongType) as "weight" )
        .agg("weight" -> "max", "weight" -> "min")
    val (highWeight, lowWeight) = {
      val b = weightBoundsTable.take(1).head
      (b.getLong(0), b.getLong(1))
    }

    val colorized = pixelized.select(
        pixelized("pixel"),
        new Column(
          UnresolvedFunction("st_colorize", Seq(
            /* Current Value */
            Subtract(
              Cast(UnresolvedAttribute("weight"), LongType),
              Cast(Literal(lowWeight), LongType)
            ),
            /* Max Value */
            Cast(Literal(highWeight - lowWeight), LongType)
          ), false)
        ) as "color"
      )

    val rendered = 
      colorized.select(
        expr("st_render(pixel, color)")
      )
      .take(1).head
      .get(0)
      .asInstanceOf[ImageSerializableWrapper].getImage

    val imageBuffer = new ByteArrayOutputStream()
    ImageIO.write(rendered, "png", imageBuffer)

    val imageURL = s"data:image/png;base64,${Base64.getEncoder.encodeToString(imageBuffer.toByteArray)}"
    val envelope = bounds.getEnvelopeInternal()

    return s"L.imageOverlay('$imageURL', [[${envelope.getMinX()},${envelope.getMinY()}], [${envelope.getMaxX()},${envelope.getMaxY()}]])"
  }


  def predictProvenance(arguments: Arguments) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .andNothingElse
}

