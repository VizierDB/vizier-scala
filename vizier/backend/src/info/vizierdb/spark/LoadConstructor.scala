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
package info.vizierdb.spark

import play.api.libs.json._
import java.io.File
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ SparkSession, DataFrame, Dataset }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.DataFrameReader
import org.apache.spark.sql.execution.datasources.csv.TextInputCSVDataSource
import org.apache.spark.sql.TypedColumn
import org.apache.spark.sql.catalyst.csv.CSVOptions
import org.apache.spark.sql.catalyst.csv.CSVHeaderChecker
import org.apache.spark.sql.execution.datasources.csv.CSVUtils
import info.vizierdb.commands.FileArgument
import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.mimirdb.caveats.implicits._
import org.mimirdb.lenses.inference.InferTypes
import info.vizierdb.catalog.Artifact

case class LoadConstructor(
  url: FileArgument,
  format: String,
  sparkOptions: Map[String, String],
  contextText: Option[String] = None,
  proposedSchema: Option[Seq[StructField]] = None,
  urlIsRelativeToDataDir: Option[Boolean] = None,
  projectId: Identifier
) 
  extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{
  lazy val (absoluteUrl: String, _) = 
    url.getPath(
      projectId = projectId,
      noRelativePaths = true
    )
  lazy val schema = construct().schema

  def construct(context: Identifier => Artifact): DataFrame = construct()
  def construct(): DataFrame =
  {
    var df =
      format match {
        case DatasetFormat.CSV => loadCSVWithCaveats()
        case _ => loadWithoutCaveats()
      }

    return df
  }


  def dependencies = Set.empty

  /**
   * Merge the proposed schema with the actual schema obtained from the
   * data loader to obtain the actual schema we should emit.
   *
   * If the proposed schema is too short, fields off the end will use 
   * their default names.
   * 
   * If the proposed schema is too long, it will be truncated.
   *
   * In either case, duplicate names will have _# appended
   */
  def actualizeProposedSchema(currSchema: Seq[StructField]): Seq[StructField] =
  {
    val schema: Seq[StructField] = 
      proposedSchema match { 
        case None => currSchema
        case Some(realProposedSchema) => 
          val targetSchemaFields: Seq[StructField] = 
            if(realProposedSchema.size > currSchema.size){
              realProposedSchema.take(currSchema.size)
            } else if(realProposedSchema.size < currSchema.size){
              realProposedSchema ++ currSchema.toSeq.drop(realProposedSchema.size)
            } else {
              realProposedSchema
            }
          assert(currSchema.size == targetSchemaFields.size)
          targetSchemaFields
      }

    val duplicateNames:Seq[String] = 
      schema.groupBy { column => column.name.toLowerCase }
            .map { case (name, elems) => (name, elems.size) }
            .filter { _._2 > 1 }
            .map { _._1 }
            .toSeq

    logger.trace(s"PROPOSED SCHEMA: $schema")

    if(duplicateNames.isEmpty){ return schema }
    else {
      logger.trace(s"Duplicate Names: $duplicateNames")
      var duplicateNameMap = duplicateNames.map { _ -> 1 }.toMap
      return schema.map { column => 
                duplicateNameMap.get(column.name.toLowerCase) match {
                  case None => column
                  case Some(idx) => 
                    duplicateNameMap = duplicateNameMap + (column.name.toLowerCase -> (idx+1))
                    StructField(
                      s"${column.name}_$idx", 
                      column.dataType, 
                      column.nullable, 
                      column.metadata
                    )
                }
              }
    }
  }

  def convertToProposedSchema(df: DataFrame): DataFrame =
  {
    if(proposedSchema.isEmpty || proposedSchema.get.equals(df.schema.fields.toSeq)){
      return df
    } else {
      val currSchema = df.schema.fields
      val targetSchemaFields = actualizeProposedSchema(currSchema)

      df.select(
        (targetSchemaFields.zip(currSchema).map { 
          case (target, curr) => 
            if(target.dataType.equals(curr.dataType)){
              if(target.name.equals(curr.name)){
                df(curr.name)
              } else {
                df(curr.name).as(target.name)
              }
            } else {
              import org.mimirdb.lenses.implicits._
              df(curr.name)
                .castWithCaveat(
                  target.dataType, 
                  s"${curr.name} from ${contextText.getOrElse(url)}"
                )
                .as(target.name)
            }
      }):_*)
    }

  }

  def loadWithoutCaveats(): DataFrame = 
  {
    var parser = Vizier.sparkSession.read.format(format)
    for((option, value) <- sparkOptions){
      parser = parser.option(option, value)
    }
    var df = parser.load(absoluteUrl)
    df = convertToProposedSchema(df)
    return df
  }

  val LEADING_WHITESPACE = raw"^[ \t\n\r]+"
  val INVALID_LEADING_CHARS = raw"^[^a-zA-Z_]+"
  val INVALID_INNER_CHARS = raw"[^a-zA-Z0-9_]+"

  def cleanColumnName(name: String): String =
    name.replaceAll(LEADING_WHITESPACE, "")
        .replaceAll(INVALID_LEADING_CHARS, "_")
        .replaceAll(INVALID_INNER_CHARS, "_")

  def withInferredTypes: LoadConstructor =
  {
    val df = construct(_ => ???)
    val unproposedSchema = 
      df.schema.drop(proposedSchema.map { _.size }.getOrElse(0))
    
    if(unproposedSchema.isEmpty){ return this }

    val columnsToGuess = 
      unproposedSchema.filter { _.dataType == StringType }
                      .map { _.name }

    val inferred = 
      InferTypes(df, attributes = columnsToGuess)
        .map { c => c.name -> c }
        .toMap

    return copy(
      proposedSchema = 
        Some(
          proposedSchema.getOrElse(Seq.empty) ++ 
            unproposedSchema.map { c => 
              inferred.getOrElse(c.name, c)
            }
        )
    )
  }

  def loadCSVWithCaveats(): DataFrame =
  {
    // based largely on Apache Spark's DataFrameReader's csv(Dataset[String]) method
    val spark = Vizier.sparkSession

    logger.debug(s"LOADING CSV FILE: $url ($absoluteUrl)")

    import spark.implicits._
    val ERROR_COL = "__MIMIR_CSV_LOAD_ERROR"
    val extraOptions = Map(
      "mode" -> "PERMISSIVE",
      "columnNameOfCorruptRecord" -> ERROR_COL
    )

    val options = 
      new CSVOptions(
        extraOptions ++ sparkOptions,
        spark.sessionState.conf.csvColumnPruning,
        spark.sessionState.conf.sessionLocalTimeZone
      )
    val data: DataFrame = 
      spark.read
           .format("text")
           .load(absoluteUrl)
    
    logger.trace(s"SCHEMA: ${data.schema}")

    val maybeFirstLine = 
      if(options.headerFlag){
        data.take(1).headOption.map { _.getAs[String](0) }
      } else { None }

    logger.trace(s"Maybe First Line: $maybeFirstLine")

    val baseSchema = 
      actualizeProposedSchema(
        TextInputCSVDataSource.inferFromDataset(
          spark, 
          data.map { _.getAs[String](0) },
          data.take(1).headOption.map { _.getAs[String](0) },
          options
        ).map { column => 
          StructField(cleanColumnName(column.name), column.dataType)
        }
      )

    logger.trace(s"BASE SCHEMA: ${baseSchema}")

    val stringSchema = 
      baseSchema.map { field => StructField(field.name, StringType) }

    val annotatedSchema = 
      stringSchema :+ StructField(ERROR_COL, StringType)

    val dataWithoutHeader = 
      maybeFirstLine.map { firstLine => 
        val headerChecker = new CSVHeaderChecker(
          StructType(baseSchema),
          options,
          source = contextText.getOrElse { "CSV File" }
        )

        headerChecker.checkHeaderColumnNames(firstLine)

        AnnotateWithSequenceNumber.withSequenceNumber(data) { 
          _.filter(col(AnnotateWithSequenceNumber.ATTRIBUTE) =!= 
                    AnnotateWithSequenceNumber.DEFAULT_FIRST_ROW)
        }
      }.getOrElse { data }

    dataWithoutHeader
      .select(
        col("value") as "raw",
        // when(col("value").isNull, null)
        // .otherwise(
          from_csv( 
            col("value"), 
            StructType(annotatedSchema), 
            extraOptions ++ sparkOptions 
          )
        // ) 
      as "csv"
      ).caveatIf(
        concat(
          lit("Error Loading Row: '"), 
          col("raw"), 
          lit(s"'${contextText.map { " (in "+_+")" }.getOrElse {""}}")
        ),
        col("csv").isNull or not(col(s"csv.$ERROR_COL").isNull)
      )
        .select(
        baseSchema.map { field => 
              import org.mimirdb.lenses.implicits._
          // when(col("csv").isNull, null)
            // .otherwise(
              col("csv").getField(field.name)
                        // Casting to string is a no-op, but is required here
                        // because castWithCaveat needs to know the column
                        // type, and dataWithCsvStruct isn't properly analyzed
                        // yet.  
                        .cast(StringType) 
                        .castWithCaveat(
                          field.dataType,
                          s"${field.name} from ${contextText.getOrElse(url)}"
                        )
            // )
                        .as(field.name)
        }:_*
      )
  }
}

object LoadConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[LoadConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[LoadConstructor]

}