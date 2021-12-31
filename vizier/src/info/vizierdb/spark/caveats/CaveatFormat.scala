package info.vizierdb.spark.caveats

import play.api.libs.json._
import org.mimirdb.caveats.{ Caveat }
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.types.DataType
import info.vizierdb.spark.SparkPrimitive


object CaveatFormat
{
  private implicit val literalFormat: Format[Literal] = Format(
    new Reads[Literal] { 
      def reads(literal: JsValue): JsResult[Literal] = {
        val fields = literal.as[Map[String,JsValue]]
        // Note, we *want* the quotation marks and escapes on the following 
        // line, since spark annoyingly hides the non-json version from us.
        val t = DataType.fromJson(fields("dataType").toString) 
        JsSuccess(
          Literal(SparkPrimitive.decode(fields("value"), t))
        )
      }
    },
    new Writes[Literal] {
      def writes(literal: Literal): JsValue = {
        Json.obj( 
          "dataType" -> literal.dataType.typeName,
          "value" -> SparkPrimitive.encode(literal.value, literal.dataType)
        )
      }
    }
  )

  implicit val caveatFormat: Format[Caveat] = Json.format
}