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
package info.vizierdb.commands.sample

import scalikejdbc._
import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.SparkPrimitive
import scala.util.Random
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject
import info.vizierdb.catalog.CatalogDB

object AutomaticStratifiedSample extends Command
  with LazyLogging
{
  val PAR_INPUT_DATASET = "input_dataset"
  val PAR_STRATIFICATION_COL = "stratification_column"
  val PAR_SAMPLE_RATE = "sample_rate"
  val PAR_OUTPUT_DATASET = "output_dataset"
  val PAR_SEED = "seed"

  def name: String = "Auto-Stratified Sample"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PAR_INPUT_DATASET, name = "Input Dataset"),
    ColIdParameter(id = PAR_STRATIFICATION_COL, name = "Column"),
    DecimalParameter(id = PAR_SAMPLE_RATE, default = Some(0.1), required = false, name = "Sampling Rate (0.0-1.0)"),
    StringParameter(id = PAR_OUTPUT_DATASET, required = false, name = "Output Dataset"),
    StringParameter(id = PAR_SEED, hidden = true, required = false, default = None, name = "Sample Seed"),
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.pretty(PAR_SAMPLE_RATE)} SAMPLE OF ${arguments.get[String](PAR_INPUT_DATASET)}"+
    s"STRATIFIED ON ${arguments.get[String](PAR_STRATIFICATION_COL)}"+
    (if(arguments.contains(PAR_OUTPUT_DATASET)) {
      s" AS ${arguments.pretty(PAR_OUTPUT_DATASET)}"
    } else { "" })
  def title(arguments: Arguments): String = 
    arguments.getOpt[String](PAR_OUTPUT_DATASET)
             .map { output => s"Sample from ${arguments.pretty(PAR_INPUT_DATASET)} into $output" }
             .getOrElse { s"Sample from ${arguments.pretty(PAR_INPUT_DATASET)}" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val inputName = arguments.get[String](PAR_INPUT_DATASET)
    val outputName = arguments.getOpt[String](PAR_OUTPUT_DATASET)
                              .getOrElse { inputName }
    val probability = arguments.get[Float](PAR_SAMPLE_RATE)
    val seedMaybe = arguments.getOpt[String](PAR_SEED).map { _.toLong }
    val seed = seedMaybe.getOrElse { Random.nextLong }
    val stratifyOn = arguments.get[String](PAR_STRATIFICATION_COL)

    val input = context.artifact(inputName)
                       .getOrElse { throw new IllegalArgumentException(s"No such dataset $inputName")}

    context.message("Computing strata...")

    val df = CatalogDB.withDB { implicit s => input.dataframe }()
    val col = df.schema(stratifyOn)
    val strata = df.groupBy(df(stratifyOn))
                   .count()
                   .collect()
                   .map { row => 
                     (
                       SparkPrimitive.encode(row.get(0), col.dataType),
                       row.getAs[Long](1)
                     )
                   }
                   .toMap

    val count = strata.values.sum
    val goalPerBin = count * probability / strata.size

    val undersuppliedStrata = 
      strata.filter { case (_, size) => size < goalPerBin }.keys


    if(undersuppliedStrata.size > 0){
      val maxSafeRate = strata.values.min * strata.size / count

      throw new RuntimeException(s"Sampling rate too high (max safe rate = $maxSafeRate).  Too few records for the following bins: ${undersuppliedStrata.take(4).mkString(", ")}${if(undersuppliedStrata.size > 4){", ..."} else {""}}")
    }

    context.message("Registering sample...")
    val output = context.outputDataset(
                    outputName,
                    SampleConstructor(
                      seed = seed,
                      mode = StratifiedOn(stratifyOn, 
                        strata.mapValues { size => 
                          goalPerBin.toDouble / size
                        }.toSeq
                      ),
                      input = input.id,
                      schema = df.schema
                    )
                  )
    if(seedMaybe.isEmpty) { 
      context.updateArguments(PAR_SEED -> seed.toString)
    }

    context.message("Sample created")
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PAR_INPUT_DATASET))
      .definitelyWrites(
        arguments.getOpt[String](PAR_OUTPUT_DATASET)
                 .getOrElse { arguments.get[String](PAR_INPUT_DATASET) }
      )
      .andNothingElse
}

