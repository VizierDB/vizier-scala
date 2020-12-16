package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import java.util.UUID
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.{
  MissingValueLensConfig,
  MissingValueImputerConfig
}
import org.mimirdb.lenses.Lenses

object MissingValue
  extends LensCommand
{
  def name = "Impute Missing Values"
  def lens = Lenses.missingValue

  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = "columns", name = "Columns", components = Seq(
      TemplateParameters.COLUMN,
      EnumerableParameter(id = "model", name = "Model", values = EnumerableValue.withNames(
        "<Pick One For Me>"      -> s"__PICKONE__",
        "Mean"                   -> "MeanMedianImputer/mean",
        "Median"                 -> "MeanMedianImputer/median",
        "Naive Bayes"            -> "MulticlassImputer/NaiveBayes",
        "Random Forest"          -> "MulticlassImputer/RandomForest",
        "Decision Tree"          -> "MulticlassImputer/DecisionTree",
        "Gradient Boosted Tree"  -> "MulticlassImputer/GradientBoostedTreeBinary",
        "Logistic Regression"    -> "MulticlassImputer/LogisticRegression",
        "One vs Rest"            -> "MulticlassImputer/OneVsRest",
        "Linear SVM"             -> "MulticlassImputer/LinearSupportVectorMachineBinary",
        "MultilayerPerceptron"   -> "MulticlassImputer/MultilayerPerceptron"
      ), default = Some(0), required = false),
    )),
    StringParameter(id = "uuid", name = "SavedModel", required = false, hidden = true)
  )

  val MODEL = "([a-zA-Z]+)/([a-zA-Z]+)".r

  def lensConfig(arguments: Arguments, schema: Seq[StructField], datset: String, context: ExecutionContext): JsValue =
  {
    Json.toJson(
      MissingValueLensConfig(
        columns = 
          arguments.getList("columns")
                   .map { config =>
                      val col = schema(config.get[Int]("column")).name
                      config.get[String]("model") match {
                        case "__PICKONE__" => 
                          MissingValueImputerConfig(None, col, "")
                        case MODEL(model, strategy) => 
                          MissingValueImputerConfig(Some(model), col, strategy)
                      }
                   },
        uuid = arguments.getOpt[String]("uuid")
                        .map { UUID.fromString(_) }
      )
    )
  }

  def lensFormat(arguments: Arguments): String = 
    s"IMPUTE MISSING VALUES ON ${arguments.getList("columns").map { "COLUMN "+_.get[Int]("column") }.mkString(", ")}"

  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], datset: String): Map[String,JsValue] = 
  {
    val config = lensArgs.as[MissingValueLensConfig]
    Map(
      "uuid" -> JsString(config.uuid.get.toString),
      "columns" -> JsArray(config.columns.map { col => 
        Json.obj(
          "column" -> JsNumber(schema.indexWhere { _.name.equalsIgnoreCase(col.imputeCol) }),
          "model" -> (col.modelType.get + "/" + col.strategy)
        )
      })
    )
  }
}