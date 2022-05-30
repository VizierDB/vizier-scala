package info.vizierdb.spark

import org.apache.spark.sql.SparkSession
import org.apache.sedona.sql.utils.{Adapter, SedonaSQLRegistrator}
import org.apache.sedona.viz.core.Serde.SedonaVizKryoRegistrator
import org.apache.sedona.viz.sql.utils.SedonaVizRegistrator
import org.apache.spark.serializer.KryoSerializer
import org.mimirdb.caveats.Caveats
import org.apache.spark.sql.types.UDTRegistration
import java.awt.image.BufferedImage
import info.vizierdb.spark.udt.ImageUDT
import org.apache.spark.sql.functions.udf
import org.apache.spark.ml.linalg.{SparseVector, Vector, Vectors}
import org.apache.spark.mllib.linalg.{Vector => OldVector}

object InitSpark
{
  def local: SparkSession =
  {
    SparkSession.builder
      .appName("Vizier")
      //.config("spark.ui.port", "4041")
      //.config("spark.eventLog.enabled", "true")
      //.config("spark.eventLog.longForm.enabled", "true")
      .config("spark.serializer", classOf[KryoSerializer].getName)
      .config("spark.kryo.registrator", classOf[SedonaVizKryoRegistrator].getName)
      .config("spark.kryoserializer.buffer.max", "2000m")
      .master("local[*]")
      .getOrCreate()
  }

  def initPlugins(sparkSession: SparkSession)
  {
    //Set credential providers for tests that load from s3
    sparkSession.conf.set("fs.s3a.aws.credentials.provider", 
        "com.amazonaws.auth.EnvironmentVariableCredentialsProvider,"+
        "org.apache.hadoop.fs.s3a.SharedInstanceProfileCredentialsProvider,"+
        "org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider")
    SedonaSQLRegistrator.registerAll(sparkSession)
    SedonaVizRegistrator.registerAll(sparkSession)
    System.setProperty("geospark.global.charset", "utf8")
    Caveats.registerAllUDFs(sparkSession)
    UDTRegistration.register(classOf[BufferedImage].getName, classOf[ImageUDT].getName)

    sparkSession.udf.register("vector_to_array", vectorToArrayUdf)
    sparkSession.udf.register("array_to_vector", arrayToVectorUdf)
  }

  // For some blasted reason these are private in 
  // https://github.com/apache/spark/blob/master/mllib/src/main/scala/org/apache/spark/ml/functions.scala
  val vectorToArrayUdf = udf { vec: Any =>
    vec match {
      case v: Vector => v.toArray
      case v: OldVector => v.toArray
      case v => throw new IllegalArgumentException(
        "function vector_to_array requires a non-null input argument and input type must be " +
        "`org.apache.spark.ml.linalg.Vector` or `org.apache.spark.mllib.linalg.Vector`, " +
        s"but got ${ if (v == null) "null" else v.getClass.getName }.")
    }
  }.asNonNullable()

  val arrayToVectorUdf = udf { array: Seq[Double] =>
    Vectors.dense(array.toArray)
  }
}
