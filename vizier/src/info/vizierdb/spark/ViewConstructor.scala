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

case class ViewConstructor(
  datasets: Map[String, Identifier],
  functions: Map[String, Identifier],
  query: String,
  projectId: Identifier
) extends DataFrameConstructor
  with DefaultProvenance
{

  def construct(context: Identifier => DataFrame): DataFrame =
  {
    var df
      = InjectedSparkSQL(
          sqlText = query, 
          tableMappings = datasets.mapValues { id => () => context(id) },
          allowMappedTablesOnly = true,
          functionMappings = 
            functions.map { case (name, blobID) => 
                        name -> 
                        { args:Seq[Expression] => 
                            val artifact = DB.readOnly { implicit s => Artifact.get(blobID, Some(projectId)) }

                            artifact.mimeType match {
                              case MIME.PYTHON => {
                                PythonProcess.udfBuilder(artifact, name)(args)
                              }
                              case _ => 
                                throw new IllegalArgumentException(s"Unsupported user-defined-function type ${artifact.mimeType}")
                            }

                        }
                      }
                      .toMap
        )
    df = AnnotateImplicitHeuristics(df)
    df = ResolveLifts(df)
    return df 
  }

  lazy val dependencies:Set[Identifier] = {
    val (viewDeps, fnDeps) =
      InjectedSparkSQL.getDependencies(query)
    
    viewDeps.map { _.toLowerCase }.map { datasets(_) }.toSet ++
      fnDeps.map { _.toLowerCase }.map { functions(_) }.toSet
  }
}
object ViewConstructor
{
  implicit val format: Format[ViewConstructor] = Json.format
}
