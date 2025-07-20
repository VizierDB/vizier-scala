/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.commands.mimir.geocoder

import java.io.File
import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.{ StructField, StringType }
import org.apache.spark.sql.DataFrame
import scala.util.Random
import info.vizierdb.commands.mimir.LensCommand
import info.vizierdb.commands.FileArgument
import info.vizierdb.catalog.Artifact
import info.vizierdb.util.FileUtils
import info.vizierdb.spark.LoadConstructor
import info.vizierdb.types._
import org.apache.spark.sql.functions.{ 
  udf, 
  size => array_size, 
  element_at, 
  when,
  col, 
  concat, 
  lit
}
import info.vizierdb.catalog.CatalogDB

class Geocode(
  geocoders: Map[String, Geocoder], 
  cacheFormat:String = "parquet"
)
  extends LensCommand
{ 
  def name: String = "Geocode"

  val PARA_HOUSE_NUMBER       = "strnumber"
  val PARA_STREET             = "strname"
  val PARA_CITY               = "city"
  val PARA_STATE              = "state"
  val PARA_GEOCODER           = "geocoder"
  val PARA_CACHE              = "cache"

  val HOUSE = "HOUSE"
  val STREET = "STREET"
  val CITY = "CITY"
  val STATE = "STATE"
  val COORDS = "COORDS"


  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = PARA_HOUSE_NUMBER, name = "House Nr."),
    ColIdParameter(id = PARA_STREET, name = "Street"),
    ColIdParameter(id = PARA_CITY, name = "City"),
    ColIdParameter(id = PARA_STATE, name = "State"),
    EnumerableParameter(id = PARA_GEOCODER, name = "Geocoder", values = EnumerableValue.withNames(
        geocoders.values.map { g =>
          g.label -> g.name
        }.toSeq:_*
      ),
      default = Some(1),
      hidden = (geocoders.size == 1)
    ),
    CachedStateParameter(id = PARA_CACHE, name = "Address Cache", required = false, hidden = true)
  )
  def format(arguments: Arguments): String = 
    s"GEOCODE WITH ${arguments.get[String](PARA_GEOCODER)}"

  def title(arguments: Arguments): String =
    s"GEOCODE ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {

    val geocoder = arguments.getOpt[String](PARA_GEOCODER)
                            .getOrElse { geocoders.head._1 }


    val (cacheArtifact:Artifact, freshArtifact:Boolean) = 
         // Discard the old model if we're asked to reset it.
         // Otherwise, try to load the old model.
      arguments.getOpt[Identifier](PARA_CACHE)
               .map { id => 
                  CatalogDB.withDBReadOnly { implicit s => 
                    Artifact.get(id, Some(context.projectId))
                  } -> false
               }
               .getOrElse { 
         // If we don't have/can't use the old model, create a new one
         // Don't go through context to create it so we don't pollute the namespace.
                 CatalogDB.withDB { implicit s => 
                   val artifact = 
                     Artifact.make(
                       projectId = context.projectId,
                       t = ArtifactType.DATASET,
                       data = Array[Byte](),
                       mimeType = MIME.RAW
                     )
         // We use the ID of the artifact as the file location, so we're going to
         // need to overwrite the data **after** we have the id itself.
                   artifact.replaceData(
                      Json.toJson(
                        LoadConstructor(
                          url = FileArgument( fileid = Some(artifact.id) ),
                          format = cacheFormat,
                          sparkOptions = Map.empty,
                          projectId = context.projectId
                        )
                      )
                    )
                   artifact
                 } -> true
               }


    /**
     * The actual addresses in the dataset
     */
    val addresses = df.select(
      df(arguments.get[String](PARA_HOUSE_NUMBER)) as HOUSE,
      df(arguments.get[String](PARA_STREET      )) as STREET,
      df(arguments.get[String](PARA_CITY        )) as CITY,
      df(arguments.get[String](PARA_STATE       )) as STATE
    )

    /**
     * The existing cache (if available)
     */
    val cache = 
      if(freshArtifact){ null:DataFrame } else {
        CatalogDB.withDB { implicit s => cacheArtifact.dataframe }()
      }

    /**
     * Addresses that we haven't geocoded yet.
     */
    val requiredAddresses = 
      if(freshArtifact){ addresses } else {
        addresses.except(
          cache.select(
            cache(HOUSE) as HOUSE,
            cache(STREET) as STREET,
            cache(CITY) as CITY,
            cache(STATE) as STATE
          )
        )
      }

    // If this isn't the first pass AND we don't need to geocode anything, then
    // we're done.  Nothing else to do here.
    if(!freshArtifact || requiredAddresses.isEmpty){ return Map.empty }

    val geocodeFn = geocoders(geocoder).apply _
    val geocode = udf(geocodeFn)
    
    /**
     * Addresses that we need to add to our geocoding set
     */
    var newGeocodedAddresses:DataFrame = 
      requiredAddresses.select( 
                          addresses(HOUSE),
                          addresses(STREET),
                          addresses(CITY),
                          addresses(STATE),
                          geocode(
                            addresses(HOUSE).cast(StringType),
                            addresses(STREET).cast(StringType),
                            addresses(CITY).cast(StringType),
                            addresses(STATE).cast(StringType)
                          ) as COORDS
                      )
    
    // If we have addresses from a previous geocoding pass, then make sure to 
    // include them as well
    if(!freshArtifact) { 
      newGeocodedAddresses = cache union newGeocodedAddresses      
    }


    // Save all of the geocoded addresses in the cache file.  Since we're using
    // the original cache file already, generate a new file and have it replace
    // the original.
    val cacheFile = 
      cacheArtifact.relativeFile
    val tempFile = 
      new File(cacheFile.getParentFile, cacheFile.getName + ".temp")
    newGeocodedAddresses.write
                        .format(cacheFormat)
                        .save(tempFile.toString)

    if(cacheFile.exists){
      FileUtils.recursiveDelete(cacheFile)
    }
    tempFile.renameTo(cacheFile)

    Map(
      PARA_CACHE -> cacheArtifact.id
    )
  }
  def build(dataset: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    ???
  }

}

object Geocode
{

  def init(
    geocoders: Seq[Geocoder], 
    cacheFormat:String = "parquet",
    label:String = "geocode"
  )
  {
    Commands("mimir").register(
      label -> new Geocode(
        geocoders = geocoders.map { g => g.name -> g }.toMap,
        cacheFormat = cacheFormat
      ),
    )
  }
}