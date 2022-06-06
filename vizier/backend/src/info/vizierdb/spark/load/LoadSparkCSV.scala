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
import scala.collection.mutable
import info.vizierdb.catalog.Artifact

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
  override def construct(context: Identifier => Artifact): DataFrame = 
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

  def applyProposedSchema(
    baseSchema: Seq[StructField], 
    proposedSchema: Seq[StructField]
  ): Seq[StructField] =
  {
    if(proposedSchema.isEmpty){ return baseSchema }

    proposedSchema.take(baseSchema.size) ++
      cleanSchema(baseSchema).drop(proposedSchema.size)
  }

  def cleanSchema(baseSchema: Seq[StructField]): Seq[StructField] =
  {
    var schema = baseSchema
    
    schema = schema.map { col =>
      col.copy( name = 
        LoadSparkDataset.cleanColumnName(col.name)
      )
    }

    val duplicateKeys = 
      mutable.Map(
        schema.groupBy { _.name.toLowerCase }
              .filter { _._2.size > 2 }
              .keys
              .map { _ -> 1 }
              .toSeq:_*
      )

    schema = schema.map { col =>
      val name = col.name.toLowerCase
      if(duplicateKeys.contains(name)){
        val idx = duplicateKeys(name)
        duplicateKeys(name) += 1
        col.copy( name = s"${col.name}_$idx" )
      } else { col }
    }

    return schema
  }

  def infer(
    url: FileArgument, 
    projectId: Identifier, 
    contextText: String,
    header: Option[Boolean],
    proposedSchema: Seq[StructField] = Seq.empty,
    sparkOptions: Map[String, String] = Map.empty,
    guessTypes: Boolean = false,
  ): LoadSparkCSV =
  {
    val spark = Vizier.sparkSession
    import spark.implicits._

    val data: DataFrame = 
      spark.read
           .format("text")
           .load(url.getPath(projectId, noRelativePaths = true)._1)


    val head = data.take(1).headOption.map { _.getAs[String](0) }
    var schema:Seq[StructField] = 
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
      ).fields

    schema = 
      cleanSchema(schema)

    if(!guessTypes){ 
      schema = schema.map { _.copy(dataType = StringType) }
    }

    schema = 
      applyProposedSchema(schema, proposedSchema)

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
      sparkOptions = sparkOptions
    )
  }
}