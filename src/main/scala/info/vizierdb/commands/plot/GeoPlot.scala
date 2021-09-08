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
import org.apache.spark.sql.functions.{ expr, first, lit, count }
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
import info.vizierdb.util.StringUtils

object GeoPlot extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_LAYERS = "layers"
  val PARAM_FORMAT = "layer_type"
  val PARAM_NAME = "layer_name"
  val PARAM_SHAPE_COL = "shape_column"
  val PARAM_WEIGHT_COL = "weight_column"

  val LAYER_VECTOR = "vector"
  val LAYER_HEATMAP = "heat_map"

  val POINT_LIMIT_THRESHOLD = 500

  def name: String = "Plot Data on Map"
  def parameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_LAYERS, name = "Layers", components = Seq(
      DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
      StringParameter(id = PARAM_NAME, name = "Layer Name", required = false),
      EnumerableParameter(id = PARAM_FORMAT, name = "Layer Type", values = EnumerableValue.withNames(
        "Vector (standard)" -> LAYER_VECTOR,
        "Raster Heat Map" -> LAYER_HEATMAP,
      ), default = Some(0)),
      ColIdParameter(id = PARAM_SHAPE_COL, name = "Feature Column"),
    ))
  )
  def format(arguments: Arguments): String = 
  {
    val columns = arguments.getList(PARAM_LAYERS)
                            .map { a => a.pretty(PARAM_DATASET) + "." +
                                          a.pretty(PARAM_SHAPE_COL) }
    s"PLOT MAP DATA FROM ${StringUtils.oxfordComma(columns)}"
  }
  def title(arguments: Arguments): String = 
  {
    val columns = arguments.getList(PARAM_LAYERS)
                            .map { a => a.pretty(PARAM_DATASET) + "." +
                                          a.pretty(PARAM_SHAPE_COL) }
    s"Plot ${StringUtils.oxfordComma(StringUtils.ellipsize(columns, 2))} on map"
  }

  def fileNameForIndex(idx: Int) = s"geojson_$idx"

  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    var bounds: Envelope = null

    val layers = 
      arguments.getList(PARAM_LAYERS).zipWithIndex.map { case (layer, idx) =>
        val datasetName = layer.get[String](PARAM_DATASET)
        var df = context.dataframe(datasetName)
        val shapeColumnIdx = layer.get[Int](PARAM_SHAPE_COL)
        val shapeColumn = df.schema.fields(shapeColumnIdx)
        val weightColumnIdx: Option[Int] = None // layer.getOpt[Int](PARAM_WEIGHT_COL)

        df = df.filter(df(shapeColumn.name).isNotNull)


        val nullSafeDF = df.filter(df(shapeColumn.name).isNotNull)

        val boundsTable = nullSafeDF.agg(shapeColumn.name -> "st_envelope_aggr")
        val envelope = boundsTable.take(1).head.get(0).asInstanceOf[Geometry].getEnvelopeInternal()
        val localBounds = boundsTable.take(1).head.get(0).asInstanceOf[Geometry]
        val envelope = localBounds.getEnvelopeInternal()
        if(bounds == null){
          bounds = envelope
        } else {
          bounds.expandToInclude(envelope)
        }

        val features =
          FeatureCollection( 
            nullSafeDF.take(POINT_LIMIT_THRESHOLD + 1)
              .map { row => 
                Feature(
                  geometry = row.getAs[Geometry](shapeColumnIdx),
                  properties = Json.obj(
                    "popup" -> (
                      "<table>"+
                      nullSafeDF
                        .columns
                        .zipWithIndex
                        .filterNot { _._2 == shapeColumnIdx }
                        .map { case (field, idx) => 
                          s"<tr><th>$field</th><td>${row.get(idx)}</td></tr>"
                        }
                        .mkString("\n")+
                      "</table>"
                    )
                  )
                )
              }
          )

        if(features.features.length > POINT_LIMIT_THRESHOLD){
          context.message(s"More than $POINT_LIMIT_THRESHOLD points in $datasetName; Not showing all points.")
        }

        val layerName = layer.getOpt[String](PARAM_NAME)
                             .getOrElse { shapeColumn.name.replaceAll("[^\"]", "") }

        layer.get[String](PARAM_FORMAT) match {
          case LAYER_VECTOR => genVectorLayer(df, context, shapeColumnIdx, layerName, fileNameForIndex(idx))
          case LAYER_HEATMAP => genRasterLayer(df, context, shapeColumnIdx, weightColumnIdx, localBounds, layerName, fileNameForIndex(idx))

        }

      }

    println(layers.mkString("\n"))

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

  def genVectorLayer(
    df: DataFrame,
    context: ExecutionContext,
    shapeColumnIdx: Int,
    layerName: String,
    layerFile: String
  ): String = 
  {
    val features =
      FeatureCollection( 
        df.take(POINT_LIMIT_THRESHOLD + 1)
          .map { row => 
            Feature(
              geometry = row.getAs[Geometry](shapeColumnIdx),
              properties = Json.obj(
                "popup" -> (
                  "<table>"+
                  df.columns
                    .zipWithIndex
                    .filterNot { _._2 == shapeColumnIdx }
                    .map { case (field, idx) => 
                      s"<tr><th>$field</th><td>${row.get(idx)}</td></tr>"
                    }
                    .mkString("\n")+
                  "</table>"
                )
              )
            )
          }
      )

    if(features.features.length > POINT_LIMIT_THRESHOLD){
      context.message(s"More than $POINT_LIMIT_THRESHOLD points for $layerName; Not showing all points.")
    }

    val layer =
      context.outputFile(layerFile, mimeType = MIME.JSON) { 
        _.write(Json.toJson(features).toString.getBytes)
      }

    s"""
    |{
    |  let layer = L.geoJson()
    |  layers["${layerName}"] = layer
    |  layer.bindPopup(function(layer) { return layer.feature.properties.popup; })
    |  layer.addTo(theMap)
    |  
    |  let oReq = new XMLHttpRequest()
    |  oReq.onload = function(response){
    |    let data = JSON.parse(oReq.responseText)
    |    layer.addData(data)
    |  }
    |  oReq.open("GET", "${layer.url}")
    |  oReq.send()
    |}
    |""".stripMargin
  }


  def genRasterLayer(
    df: DataFrame, 
    context: ExecutionContext,
    shapeColumnIdx: Int, 
    weightColumnIdx: Option[Int], 
    bounds: Geometry,
    layerName: String,
    layerFile: String
  ): String =
  {

    val shapeColumn = df.schema(shapeColumnIdx)
    val weightColumn = weightColumnIdx.map { df.schema(_) }

    val pixelized = df.select(
        new Column(
          UnresolvedFunction("st_pixelize", Seq(
            /* Input attribute */
            UnresolvedAttribute(shapeColumn.name),
            /* width         x    height */
            Literal(256), /* x */ Literal(256),
            /* Bounds */
            Literal.fromObject(
              GeometryUDT.serialize(bounds)
            )
              
          ), false)
        ) as "pixel",
      )
      .groupBy("pixel")
      .agg( count(lit(1)) as "weight" )

    import df.sparkSession.implicits._

    val weightBoundsTable = 
      pixelized
        .select( pixelized("weight").cast(LongType) as "weight" )
        .union( Seq(0l).toDF("weight") )
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



    val layer = context.outputFile(layerFile, MIME.PNG) { 
      ImageIO.write(rendered, "png", _)
    }
    context.message(MIME.HTML, s"<a href='${layer.url}>${layerName}</a>")

    val envelope = bounds.getEnvelopeInternal()

    s"""
    |{
    |  let layer = L.imageOverlay('${layer.url}', [[${envelope.getMinX()},${envelope.getMinY()}], [${envelope.getMaxX()},${envelope.getMaxY()}]])
    |  layer.addTo(theMap)
    |}
    """.stripMargin
  }


  def predictProvenance(arguments: Arguments) = 
    ProvenancePrediction
      .definitelyReads(arguments.getList(PARAM_LAYERS)
                                .map { _.get[String](PARAM_DATASET)}:_*)
      .definitelyWrites(arguments.getList(PARAM_LAYERS)
                                 .zipWithIndex
                                 .map { x => fileNameForIndex(x._2) }:_*)
      .andNothingElse
}

