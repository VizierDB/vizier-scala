package info.vizierdb.spark
import org.apache.spark.sql.catalyst.expressions.Expression
import play.api.libs.json._
import org.apache.spark.sql.catalyst.expressions.{
  Literal,
  Cast
}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import org.apache.spark.sql.functions
object SparkExpression
{
    implicit val expressionReads = new Reads[Expression] {
        def reads(j: JsValue) =
            JsSuccess(expr(j.as[String]).expr)
    }
    implicit val expressionWrites = new Writes[Expression] {
        def writes(expression: Expression) = 
            Json.toJson(expression.toString)
    }
    implicit val expressionFormat: Format[Expression] = Format(expressionReads, expressionWrites)
}
