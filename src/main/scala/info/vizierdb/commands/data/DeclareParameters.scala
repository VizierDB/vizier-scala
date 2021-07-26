/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException
import org.mimirdb.spark.Schema.decodeType
import org.mimirdb.spark.SparkPrimitive.{ encode => sparkToJs }
import org.apache.spark.sql.catalyst.expressions.{Cast, Literal}
import info.vizierdb.viztrails.ProvenancePrediction

object DeclareParameters extends Command
{

  val PARAM_LIST = "parameters"
  val PARAM_NAME = "name"
  val PARAM_VALUE = "value"
  val PARAM_TYPE = "datatype"

  def name: String = "Declare Parameters"
  def parameters: Seq[Parameter] = Seq(
    ListParameter(name = "Parameters", id = PARAM_LIST, required = false, components = Seq(
      StringParameter(name = "Parameter Name", id  = PARAM_NAME),
      TemplateParameters.DATATYPE(PARAM_TYPE),
      StringParameter(name = "Parameter Value", id  = PARAM_VALUE),
    ))
  )
  def format(arguments: Arguments): String = 
    s"DECLARE ${arguments.getList(PARAM_LIST).map { _.get[String](PARAM_NAME) }.mkString(", ")}"
  def title(arguments: Arguments): String = 
  {
    def name(param: Arguments) = param.get[String](PARAM_NAME)
    val fieldSummary = arguments.getList(PARAM_LIST) match { 
      case Seq() => "<nothing>"
      case Seq(a) => name(a)
      case params if params.size <= 3 => params.map { name(_) }.mkString(", ")
      case params => params.take(2).map { name(_) }.mkString(", ")+", ..."
    }
    s"DECLARE ${fieldSummary}"
  }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    for(parameter <- arguments.getList(PARAM_LIST)){
      val name = parameter.get[String](PARAM_NAME)
      val dataType = decodeType(parameter.get[String](PARAM_TYPE))
      val stringValue = parameter.get[String](PARAM_VALUE)
      val sparkValue = Cast(Literal(stringValue), dataType).eval()
      context.setParameter(name, sparkValue, dataType)
      context.message(s"val $name:${dataType.sql.toLowerCase} = ${sparkToJs(sparkValue, dataType)}")
    }
  }
  def predictProvenance(arguments: Arguments) = 
    ProvenancePrediction
      .definitelyWrites(
        arguments.getList(PARAM_LIST)
                 .map { _.get[String](PARAM_NAME) }:_*
      )
      .andNothingElse
}

