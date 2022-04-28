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
package info.vizierdb.commands.mimir

import scalikejdbc._
import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import info.vizierdb.spark.vizual
import info.vizierdb.spark.vizual.VizualScriptConstructor
import info.vizierdb.catalog.CatalogDB

object Geotag extends Command
{
  val PARAM_DATASET   = "dataset"
  val PARAM_LATITUDE  = "latitude"
  val PARAM_LONGITUDE = "longitude"
  val PARAM_COLNAME   = "column"

  def name = "Geotag Records as Points"
  def parameters = Seq(
    DatasetParameter(PARAM_DATASET, "Dataset"),
    ColIdParameter(PARAM_LONGITUDE, "Longitude"),
    ColIdParameter(PARAM_LATITUDE, "Latitude"),
    StringParameter(PARAM_COLNAME, "Output Column", default = Some("geometry"))
  )

  def format(arguments: Arguments): String = 
    s"GEOTAG ${arguments.pretty(PARAM_DATASET)} WITH LATITUDE ${arguments.pretty(PARAM_LATITUDE)} LONGITUDE ${arguments.pretty(PARAM_LONGITUDE)}"
  def title(arguments: Arguments): String = 
    s"GEOTAG ${arguments.pretty(PARAM_DATASET)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val ds = context.artifact(datasetName)
                    .getOrElse { 
                      context.error(s"No dataset named '$datasetName'")
                      return
                    }

    val schema = CatalogDB.withDB { implicit s => ds.datasetSchema }
    val lat: StructField = schema(arguments.get[Int](PARAM_LATITUDE))
    val lon: StructField = schema(arguments.get[Int](PARAM_LONGITUDE))
    val outputCol = arguments.get[String](PARAM_COLNAME)

    context.outputDataset(
      datasetName,
      VizualScriptConstructor(
        Seq(
          vizual.InsertColumn(
            position = None,
            name = outputCol,
            dataType = Some(GeometryUDT)
          ),
          vizual.UpdateCell(
            column = schema.size,
            row = None,
            value = Some(JsString(
              s"=CASE WHEN `${lon.name}` IS NULL OR `${lat.name}` IS NULL THEN NULL ELSE ST_POINT(CAST(`${lon.name}` AS DECIMAL(24,20)), CAST(`${lat.name}` AS DECIMAL(24,20))) END"
            )),
            comment = None
          )
        ),
        Some(ds.id)
      )
    )

  }
  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PARAM_DATASET))
      .definitelyWrites(arguments.get[String](PARAM_DATASET))
      .andNothingElse

}