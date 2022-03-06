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
  }

}
