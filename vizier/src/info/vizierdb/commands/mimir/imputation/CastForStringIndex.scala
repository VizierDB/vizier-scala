package info.vizierdb.commands.mimir.imputation

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}
import org.apache.spark.ml.param.Param
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ DataFrame, Row, Dataset }

class CastForStringIndex(override val uid: String) 
  extends Transformer 
  with DefaultParamsWritable {
  final val inputCol= new Param[String](this, "inputCol", "The input column")
  final val outputCol = new Param[String](this, "outputCol", "The output column")
  def setInputCol(value: String): this.type = set(inputCol, value)
  def setOutputCol(value: String): this.type = set(outputCol, value)
  def this() = this(Identifiable.randomUID("castforstringindex"))
  def copy(extra: ParamMap): CastForStringIndex = defaultCopy(extra)
  override def transformSchema(schema: StructType): StructType = StructType(schema.fields.map(sfld => {
    if(sfld.name.equals($(inputCol)))
      sfld.copy(dataType = StringType)
    else sfld
  }))
  def transform(df: Dataset[_]): DataFrame = df.withColumn($(outputCol), df($(inputCol)).cast(StringType))
}
  
object CastForStringIndex extends DefaultParamsReadable[CastForStringIndex] {
  override def load(path: String): CastForStringIndex = super.load(path)
}