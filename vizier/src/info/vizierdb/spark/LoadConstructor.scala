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

case class LoadConstructor(
  url: FileArgument,
  format: String,
  sparkOptions: Map[String, String],
  contextText: Option[String] = None,
  proposedSchema: Option[Seq[StructField]] = None,
  urlIsRelativeToDataDir: Option[Boolean] = None
) 
  extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{

  lazy val absoluteUrl: String = 
    if((urlIsRelativeToDataDir.getOrElse { false })){
      MimirAPI.conf.resolveToDataDir(url).toString
    } else if( (url.charAt(0) != '/') && !url.contains(":/") 
                && MimirAPI.conf.workingDirectory.isDefined) {
      MimirAPI.conf.workingDirectory() + File.separator + url
    } else { url }

  def construct(
    spark: SparkSession, 
    context: Map[String, () => DataFrame] = Map()
  ): DataFrame =
  {
    var df =
      format match {
        case FileFormat.CSV => loadCSVWithCaveats(spark)
        case _ => loadWithoutCaveats(spark)
      }

    df = lenses.foldLeft(df) {
      (df, lens) => Lenses(lens._1.toLowerCase()).create(df, lens._2, lens._3)
    }

    return df
  }

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

  def loadWithoutCaveats(spark: SparkSession): DataFrame = 
  {
    var parser = spark.read.format(format)
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



  def loadCSVWithCaveats(spark: SparkSession): DataFrame =
  {
    // based largely on Apache Spark's DataFrameReader's csv(Dataset[String]) method

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