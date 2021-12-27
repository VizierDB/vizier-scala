package info.vizierdb.commands.mimir.imputation

import java.io.File
import org.apache.spark.ml.Transformer
import org.apache.spark.sql.DataFrame

trait MissingValueImputer {
  def impute(input:DataFrame, modelFile: File) : DataFrame
  def model(input:DataFrame, modelFile: File) : Transformer
  def name: String
}