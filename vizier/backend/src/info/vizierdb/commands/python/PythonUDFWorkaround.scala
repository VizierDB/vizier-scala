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