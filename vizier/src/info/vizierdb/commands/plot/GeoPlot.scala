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
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ Row, Column }
import org.apache.spark.sql.functions.{ expr, first }
import org.apache.spark.sql.catalyst.analysis.{ UnresolvedFunction, UnresolvedAttribute }
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.catalyst.expressions.{ Literal, Cast, Subtract }
import org.locationtech.jts.geom.Geometry
import org.apache.spark.sql.types.LongType
import org.apache.sedona.viz.core.ImageSerializableWrapper
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import info.vizierdb.types.MIME
import java.util.Base64

object GeoPlot extends Command
  with LazyLogging
{
  def name: String = "Geospatial Plot"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    ColIdParameter(id = "shape_column", name = "Shape"),
    ColIdParameter(id = "weight_column", name = "Weight"),
  )
  def format(arguments: Arguments): String = 
    s"CREATE GEOPLOT ${arguments.pretty("dataset")}.${arguments.pretty("shape_column")} COLOR BY ${arguments.pretty("weight_column")}"
  def title(arguments: Arguments): String = 
    s"Geoplot of ${arguments.pretty("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val df = context.dataframe(datasetName)
    val shape_column = df.schema.fields(arguments.get[Int]("shape_column"))
    val weight_column = df.schema.fields(arguments.get[Int]("weight_column"))

    val boundsTable = df.agg(shape_column.name -> "st_envelope_aggr")
    lazy val bounds = boundsTable.take(1).head.get(0)
    logger.debug(s"Polygon Bounds: ${bounds.toString}")

    val pixelized = df.select(
        new Column(
          UnresolvedFunction("st_pixelize", Seq(
            /* Input attribute */
            UnresolvedAttribute(shape_column.name),
            /* width         x    height */
            Literal(256), /* x */ Literal(256),
            /* Bounds */
            Literal.fromObject(
              GeometryUDT.serialize(bounds.asInstanceOf[Geometry])
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

    context.message(MIME.HTML, s"""
      <img src="data:image/png;base64,${Base64.getEncoder.encodeToString(imageBuffer.toByteArray)}">
    """)
  }


  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String]("dataset")), 
           Seq.empty) )
}

