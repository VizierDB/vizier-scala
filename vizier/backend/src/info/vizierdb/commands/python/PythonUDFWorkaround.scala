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
package org.apache.spark


import java.util.{ArrayList => JArrayList, List => JList, Map => JMap}
import org.apache.spark.sql.types._
import org.apache.spark.api.python.PythonFunction
import org.apache.spark.sql.catalyst.expressions.{ PythonUDF, Expression, ExprId, NamedExpression }
import org.apache.spark.api.python.PythonAccumulatorV2

/**
 * A utility method to construct PythonUDF objects from within Mimir
 *
 * This class exists because PythonFunction is (justifiably) package-private.  However,
 * we want to be able to import Python code, and we also don't need to tage advantage of
 * the full range of functionality available through PythonUDF.  This functionality is 
 * used mainly in org.mimirdb.spark.PythonUDFBuilder
 * 
 * Key simplifications:
 *  - Broadcast variables are NOT supported.
 *  - Accumulator variables are NOT supported.
 */
object PythonUDFWorkaround
{
  def apply(
    command: Array[Byte],
    envVars: JMap[String, String],
    pythonIncludes: JList[String],
    pythonExec: String,
    pythonVer: String
  )(
    name: String,
    dataType: DataType,
    children: Seq[Expression],
    evalType: Int,
    udfDeterministic: Boolean,
    resultId: ExprId = NamedExpression.newExprId
  ): PythonUDF =
    PythonUDF(
      name = name,
      func = PythonFunction(
        command = command,
        envVars = envVars,
        pythonIncludes = pythonIncludes,
        pythonExec = pythonExec,
        pythonVer = pythonVer,
        broadcastVars = new JArrayList(),
        accumulator = null
      ),
      dataType = dataType,
      children = children,
      evalType = evalType,
      udfDeterministic = udfDeterministic,
      resultId = resultId
    )
}