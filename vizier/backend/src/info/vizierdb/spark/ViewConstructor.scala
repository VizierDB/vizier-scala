package info.vizierdb.spark

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.catalyst.expressions.Expression
import info.vizierdb.commands.python.PythonProcess
import info.vizierdb.spark.caveats.AnnotateImplicitHeuristics
import org.mimirdb.caveats.lifting.ResolveLifts
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import breeze.linalg.View
import info.vizierdb.Vizier
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructType

case class ViewConstructor(
  datasets: Map[String, Identifier],
  // For functions, we need to cache the function mime type and text to avoid reentrancy.
  functions: Map[String, (Identifier, String, String)],
  query: String,
  projectId: Identifier,
  schema: Seq[StructField]
) extends DataFrameConstructor
  with DefaultProvenance
{

  def construct(context: Identifier => DataFrame): DataFrame =
  {
    var df = ViewConstructor.buildBase(
                query,
                datasets.mapValues { id => () => context(id) },
                functions
              )
    df = AnnotateImplicitHeuristics(df)
    df = ResolveLifts(df)
    return df 
  }

  lazy val (viewDeps, fnDeps): (Set[String], Set[String]) =
      InjectedSparkSQL.getDependencies(query)
  lazy val dependencies:Set[Identifier] = {
    viewDeps.map { _.toLowerCase }.map { datasets(_) }.toSet ++
      fnDeps.map { _.toLowerCase }.map { functions(_)._1 }.toSet
  }
}
object ViewConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[ViewConstructor] = Json.format
  def apply(j: JsValue) = j.as[ViewConstructor]
  def apply(
    datasets: Map[String, Identifier],
    // For functions, we need to cache the function mime type and text to avoid reentrancy.
    functions: Map[String, (Identifier, String, String)],
    query: String,
    projectId: Identifier,
    context: Identifier => Seq[StructField]
  ): ViewConstructor = 
  {
    ViewConstructor(
      datasets,
      functions,
      query,
      projectId,
      schema = ViewConstructor.buildBase(
                  query,
                  datasets.mapValues { id => 
                    () => Vizier.sparkSession.createDataFrame(new java.util.ArrayList[Row](), StructType(context(id)))
                  },
                  functions
                ).schema
    )
  }

  def buildBase(
    query: String, 
    tableMappings: Map[String, () => DataFrame],
    functions: Map[String, (Identifier, String, String)],
  ): DataFrame =
    InjectedSparkSQL(
      sqlText = query, 
      tableMappings = tableMappings,
      allowMappedTablesOnly = true,
      functionMappings = 
        functions.map { 
          case (name, (identifier, MIME.PYTHON, code)) => 
            name -> PythonProcess.udfBuilder(identifier, code, name)
          case (name, (_, mimeType, _)) => 
            throw new IllegalArgumentException(s"Unsupported user-defined-function $name with type ${mimeType}")
        }
        .toMap
    )
}
