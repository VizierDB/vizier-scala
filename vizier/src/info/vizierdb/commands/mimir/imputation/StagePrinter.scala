package info.vizierdb.commands.mimir.imputation

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}

class StagePrinter(val sname:String, override val uid: String) 
  extends Transformer 
  with DefaultParamsWritable {
  final val stageName = new Param[String](this, "stageName", "The stage name to print")
  def this(stageName:String) = {
    this(stageName, Identifiable.randomUID("stageprinter"))
    this.setStageName(stageName)
  }
  def this() = this("", Identifiable.randomUID("stageprinter"))
  def setStageName(value: String): this.type = set(stageName, value)
  def copy(extra: ParamMap): StagePrinter = defaultCopy(extra)
  override def transformSchema(schema: StructType): StructType = schema
  def transform(df: Dataset[_]): DataFrame = {
    //println(s"\n------------------pipeline stage: ${$(stageName)}: \n${df.schema.fields.mkString("\n")}\n-----------------------------\n")
    //df.show()
    df.select(col("*"))
  }
}
  
object StagePrinter extends DefaultParamsReadable[StagePrinter] {
  override def load(path: String): StagePrinter = super.load(path)
}