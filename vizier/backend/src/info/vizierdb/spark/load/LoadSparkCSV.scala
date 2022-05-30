package info.vizierdb.spark.load

import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands.FileArgument
import info.vizierdb.types._
import info.vizierdb.serializers._
import info.vizierdb.spark._
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.apache.spark.sql.DataFrame
import info.vizierdb.Vizier
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.catalyst.csv.CSVOptions
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StringType
import org.mimirdb.caveats.implicits.dataFrameImplicits
import org.mimirdb.lenses.implicits.columnImplicits 
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import org.apache.spark.sql.execution.datasources.csv.TextInputCSVDataSource
import org.apache.spark.sql.catalyst.expressions.{ CsvToStructs, Literal }
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.Cast
import org.apache.spark.sql.catalyst.expressions.BoundReference
import org.apache.spark.sql.catalyst.InternalRow

case class LoadSparkCSV(
  url: FileArgument,
  schema: Seq[StructField],
  projectId: Identifier,
  contextText: Option[String],
  skipHeader: Boolean = true,
  sparkOptions: Map[String, String] = Map.empty,
) extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{
  override def construct(context: Identifier => DataFrame): DataFrame = 
  {
    AnnotateWithSequenceNumber.withSequenceNumber(
      Vizier.sparkSession
            .read
            .options(sparkOptions)
            .text(url.getPath(projectId, noRelativePaths = true)._1)
            .select(
              col("value") as "raw",
              from_csv(
                col("value"), 
                StructType(
                  schema.map { _.copy(dataType = StringType) } :+
                    StructField(LoadSparkCSV.ERROR_COL, StringType)
                ),
                LoadSparkCSV.OPTIONS ++ sparkOptions
              ) as "csv"
            )
            .caveatIf(
              concat(
                lit("Error Loading Row: '"), 
                col("raw"), 
                lit(s"'${contextText.map { " (in "+_+")" }.getOrElse {""}}")
              ),
              col("csv").isNull or not(col(s"csv.${LoadSparkCSV.ERROR_COL}").isNull)
            )
          .select(
            schema.map { field => 
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
                        .as(field.name)
            }:_*
          )
    )( _.filter { col(AnnotateWithSequenceNumber.ATTRIBUTE) > 0 })
  }

  override def dependencies: Set[Identifier] = Set.empty
}

object LoadSparkCSV
  extends DataFrameConstructorCodec
{

  val ERROR_COL = "__MIMIR_CSV_LOAD_ERROR"
  val OPTIONS = 
    Map(
      "mode" -> "PERMISSIVE",
      "columnNameOfCorruptRecord" -> ERROR_COL
    )

  implicit val format: Format[LoadSparkCSV] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[LoadSparkCSV]

  def train(
    url: FileArgument, 
    projectId: Identifier, 
    contextText: String,
    header: Option[Boolean],
    sparkOptions: Map[String, String] = Map.empty
  ): LoadSparkCSV =
  {
    val spark = Vizier.sparkSession
    import spark.implicits._

    val data: DataFrame = 
      spark.read
           .format("text")
           .load(url.getPath(projectId, noRelativePaths = true)._1)


    val head = data.take(1).headOption.map { _.getAs[String](0) }
    val schema = 
        TextInputCSVDataSource.inferFromDataset(
          spark, 
          data.map { _.getAs[String](0) },
          head,
          new CSVOptions(
            OPTIONS ++ sparkOptions ++ Map(
              "header" -> header.getOrElse(true).toString,
              "inferSchema" -> true.toString()
            ),
            spark.sessionState.conf.csvColumnPruning,
            spark.sessionState.conf.sessionLocalTimeZone
          )
        ).map { column => 
          StructField(
            LoadSparkDataset.cleanColumnName(column.name), 
            column.dataType
          )
        }

    val hasHeader: Boolean =
      if(head.isEmpty) { false }
      else {
        header.getOrElse { 
          val row: InternalRow = 
            CsvToStructs(
              StructType(schema.map { _.copy(dataType = StringType) }),
              OPTIONS ++ sparkOptions,
              lit(head.get).expr,
              timeZoneId = Some(spark.sessionState.conf.sessionLocalTimeZone)
            ).eval(null)
             .asInstanceOf[InternalRow]

          schema.zipWithIndex.exists { case (col, idx) => 
            val decoded =
              Cast(BoundReference(idx, StringType, col.nullable),
                col.dataType).eval(row)

            decoded == null
          }
        }
      }

    LoadSparkCSV(
      url = url,
      schema = schema,
      projectId = projectId,
      contextText = Some(contextText), 
    )
  }
}