package info.vizierdb.commands.mimir.imputation

import java.io.File
import org.apache.spark.sql.types._
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.PipelineStage
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.NumericType
import org.apache.spark.ml.classification.NaiveBayes
import org.apache.spark.ml.classification.RandomForestClassifier
import org.apache.spark.ml.classification.DecisionTreeClassifier
import org.apache.spark.ml.classification.GBTClassifier
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.classification.OneVsRest
import org.apache.spark.ml.classification.LinearSVC
import org.apache.spark.ml.classification.MultilayerPerceptronClassifier
import org.apache.spark.ml.feature.IndexToString
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.ml.feature.RegexTokenizer
import org.apache.spark.ml.feature.HashingTF
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.feature.Normalizer
import org.apache.spark.ml.feature.StandardScaler
import org.apache.spark.ml.Pipeline

case class MulticlassImputer(
  imputeCol:String, 
  strategy:String
) extends MissingValueImputer
{
  def impute(input:DataFrame, modelFile: File) : DataFrame = {
    //println(s"impute: input: ----------------\n${input.schema.fields.map(fld => (fld.name, fld.dataType)).mkString("\n")}")
    val imputeddf = model(input, modelFile).transform(input)
    imputeddf
  }
  def model(input:DataFrame, modelFile: File):Transformer = {
    if(modelFile.exists()){ 
      PipelineModel.load(modelFile.getPath)
    }
    else {
      val imputerModel = 
        MulticlassImputer.classifierPipelines(strategy)(
          input, 
          imputeCol,
          MulticlassImputerParamsHandleInvalid.keep
        )
      imputerModel.save(modelFile.getPath)
      imputerModel
    }
  }
  def name = s"MulticlassImputer/$strategy"
}

object MulticlassImputerParamsHandleInvalid extends Enumeration {
  type T = Value
  val keep, skip, error = Value
}


object MulticlassImputer {
  val PREDICTED_LABEL_COL = "predictedLabel"
  private def extractFeatures(
    training:DataFrame, 
    predictionCol: String, 
    handleInvalid: MulticlassImputerParamsHandleInvalid.T
  ):
    (Array[String], Seq[PipelineStage]) = 
  {
      val cols = training.schema.fields
      //training.show()
      val featCols = cols.filterNot(_.name.equalsIgnoreCase(predictionCol))
      val trainingIndexable = training.withColumn(predictionCol, training(predictionCol)
                                      .cast(StringType))
      val stringIndexCaster = new CastForStringIndex().setInputCol(predictionCol)
                                                      .setOutputCol(predictionCol)
      val indexer = new StringIndexer().setInputCol(predictionCol)
                                       .setOutputCol("label")
                                       .setHandleInvalid(handleInvalid.toString())
      val labels = indexer.fit(trainingIndexable).labels
      val (tokenizers, hashingTFs) = featCols.flatMap(col => {
        col.dataType match {
          case StringType => {
            val tokenizer = new RegexTokenizer().setInputCol(col.name).setOutputCol(s"${col.name}_words")
            val hashingTF = new HashingTF().setInputCol(tokenizer.getOutputCol).setOutputCol(s"${col.name}_features").setNumFeatures(20)
            Some((tokenizer, hashingTF))
          }
          case _ => None
        }
      }).unzip
      val nullReplacers = featCols.flatMap(col => {
        col.dataType match {
          case StringType => Some(new ReplaceNullsForColumn().setInputColumn(col.name).setOutputColumn(col.name).setReplacementColumn("''"))
          case x:NumericType => Some(new ReplaceNullsForColumn().setInputColumn(col.name).setOutputColumn(col.name).setReplacementColumn("0"))
          case _ => None
      }})
      val assmblerCols = featCols.flatMap(col => {
        col.dataType match {
          case StringType => Some(s"${col.name}_features")
          case x:NumericType => Some(col.name)
          case _ => None
        }
      })
      val assembler = new VectorAssembler().setInputCols(assmblerCols.toArray)
                                           .setOutputCol("rawFeatures")
                                           .setHandleInvalid(handleInvalid.toString())
      val normlzr = new Normalizer().setInputCol("rawFeatures")
                                    .setOutputCol("normFeatures")
                                    .setP(1.0)
      val scaler = new StandardScaler().setInputCol("normFeatures")
                                       .setOutputCol("features")
                                       .setWithStd(true)
                                       .setWithMean(false)
      (labels,(stringIndexCaster :: new StagePrinter("indexcast") :: indexer :: new StagePrinter("indexer") :: nullReplacers ++: tokenizers ++: hashingTFs ++: (new StagePrinter("tokhash") :: assembler :: new StagePrinter("assembler") :: normlzr :: new StagePrinter("norm") :: scaler :: Nil)))
    }

    val classifierPipelines = Map[String, (DataFrame, String, MulticlassImputerParamsHandleInvalid.T) => PipelineModel](
      ("NaiveBayes", (trainingData, predictionCol, handleInvalid) => {
        import org.apache.spark.sql.functions.abs
        val trainingp = trainingData.na.drop//.withColumn(params.predictionCol, trainingData(params.predictionCol).cast(StringType))
        val training = trainingp.schema.fields.filter(col => col.dataType match {
          case x:NumericType => true
          case _ => false
        } ).foldLeft(trainingp)((init, cur) => init.withColumn(cur.name,abs(init(cur.name))) )
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new NaiveBayes().setLabelCol("label")
                                         .setFeaturesCol("features")//.setModelType("multinomial")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol)
                                                .setOutputCol(PREDICTED_LABEL_COL)
                                                .setLabels(labels)
        val replaceNulls = new ReplaceNullsForColumn().setInputColumn(predictionCol)
                                                      .setOutputColumn(predictionCol)
                                                      .setReplacementColumn(PREDICTED_LABEL_COL)
        val stages:Seq[PipelineStage] = 
          featurePipelineStages ++: Seq[PipelineStage](
              new StagePrinter("features"),
              classifier,
              new StagePrinter("classifier"),
              labelConverter,
              new StagePrinter("labelconvert"),
              replaceNulls
            )
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
   
      ("RandomForest", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new RandomForestClassifier().setLabelCol("label")
                                                     .setFeaturesCol("features")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: (classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
  
      ("DecisionTree", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new DecisionTreeClassifier().setLabelCol("label").setFeaturesCol("features")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
  
      ("GradientBoostedTreeBinary", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val featureIndexer = new VectorIndexer().setInputCol("assembledFeatures").setOutputCol("features").setMaxCategories(20)
        val classifier = new GBTClassifier().setLabelCol("label").setFeaturesCol("features").setMaxIter(10)
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( featureIndexer :: classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(
          training.withColumn(predictionCol, training(predictionCol).cast(StringType))
        )
      }),
      
      ("LogisticRegression", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new LogisticRegression().setMaxIter(10).setTol(1E-6).setFitIntercept(true).setLabelCol("label").setFeaturesCol("features")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
      
      ("OneVsRest", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new LogisticRegression().setMaxIter(10).setTol(1E-6).setFitIntercept(true).setLabelCol("label").setFeaturesCol("features")
        val ovr = new OneVsRest().setClassifier(classifier)
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( ovr :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
      
      ("LinearSupportVectorMachineBinary", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        val classifier = new LinearSVC().setMaxIter(10).setRegParam(0.1).setLabelCol("label").setFeaturesCol("features")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }),
      
      ("MultilayerPerceptron", (trainingData, predictionCol, handleInvalid) => {
        val training = trainingData.na.drop
        val (labels, featurePipelineStages) = extractFeatures(training, predictionCol, handleInvalid)
        import org.apache.spark.sql.functions.countDistinct
        import org.apache.spark.sql.functions.col
        val classCount = training.select(countDistinct(col(predictionCol))).head.getLong(0)
        val layers = Array[Int](training.columns.length, 8, 4, classCount.toInt)
        val classifier = new MultilayerPerceptronClassifier()
          .setLayers(layers).setBlockSize(128).setSeed(1234L).setMaxIter(100).setLabelCol("label").setFeaturesCol("features")
        val labelConverter = new IndexToString().setInputCol(classifier.getPredictionCol).setOutputCol(PREDICTED_LABEL_COL).setLabels(labels)
        val stages = featurePipelineStages ++: ( classifier :: labelConverter :: Nil)
        val pipeline = new Pipeline().setStages(stages.toArray)
        pipeline.fit(training)
      }))
}