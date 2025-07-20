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