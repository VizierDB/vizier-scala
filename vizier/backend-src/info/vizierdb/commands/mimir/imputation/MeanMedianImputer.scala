package info.vizierdb.commands.mimir.imputation

import java.io.File
import org.apache.spark.sql.DataFrame
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.feature.ImputerModel
import org.apache.spark.ml.feature.Imputer

case class MeanMedianImputer(imputeCol:String, strategy:String) 
  extends MissingValueImputer
{

  def impute(input:DataFrame, modelFile: File) : DataFrame = {
      model(input, modelFile).transform(input)
  }
  def model(input:DataFrame, modelFile: File):Transformer = {
    if(modelFile.exists()) 
      ImputerModel.load(modelFile.getPath)
    else {
      val imputer = new Imputer()
        .setStrategy(strategy)
        .setMissingValue(0)
        .setInputCols(Array(imputeCol)).setOutputCols(Array(imputeCol));
      val fieldRef = input(imputeCol)
      val imputerModel = imputer.fit(input.filter(fieldRef.isNotNull))
      imputerModel.save(modelFile.getPath)
      imputerModel
    }
  }
  def name = s"MeanMedianImputer/$strategy"
}