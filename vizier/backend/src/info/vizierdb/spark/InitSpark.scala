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
import info.vizierdb.Vizier
import java.io.File
import org.apache.spark.sql.catalyst.FunctionIdentifier
import com.typesafe.scalalogging.LazyLogging

object InitSpark
  extends LazyLogging
{
  def local: SparkSession =
  {
    val warehouseDir = 
      Vizier.config.warehouseDirOverride.toOption
            .getOrElse { new File(Vizier.config.cacheDirFile, "spark-warehouse") }

    if(!warehouseDir.exists()){ warehouseDir.mkdirs() }
    assert(warehouseDir.exists())

    val session = SparkSession.builder
      .appName("Vizier")
      //.config("spark.ui.port", "4041")
      //.config("spark.eventLog.enabled", "true")
      //.config("spark.eventLog.longForm.enabled", "true")
      .config("spark.serializer", classOf[KryoSerializer].getName)
      .config("spark.kryo.registrator", classOf[SedonaVizKryoRegistrator].getName)
      .config("spark.kryoserializer.buffer.max", "2000m")
      .master("local[*]")
      .config("spark.sql.warehouse.dir", warehouseDir.getAbsolutePath())
      .getOrCreate()

    // For some silly reason, Hadoop needs some poking to make the local 
    // filesystem visible to it.
    // c.f. https://stackoverflow.com/questions/17265002/hadoop-no-filesystem-for-scheme-file
    session.sparkContext.hadoopConfiguration.set(
      "fs.file.impl", 
      classOf[org.apache.hadoop.fs.LocalFileSystem].getName
    )

    // WORKAROUND: Java11+ seems to introduce a security measure that partitions 
    // classloaders, possibly for each individual jar.  Specifically, the App's
    // classloader in Java11 is only aware of files in the app jar and any 
    // direct dependencies.  This wouldn't be a problem if Spark is run natively
    // (i.e., using the spark runner), but is in our case, since the App 
    // classloader only holds Vizier classes.  Work around by temporarily 
    // disabling the thread local classloader and materializing the relevant
    // state.  With the thread local classloader disabled, Spark's 
    // Utils.classByName will fall back to the classloader that loaded Utils
    // itself, which should be the correct one.  This shouldn't pose any 
    // concurrency issues, since initialization is unlikely to be multithreaded.
    //
    // See https://github.com/VizierDB/vizier-scala/issues/179

    // Disable thread-local classloader.  
    val originalClassloader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(null)

    // Force materialization
    session.sharedState.externalCatalog

    // TODO: In principle, we should be able to avoid the workaround above by
    // adding the Vizier URL to the session.sharedState.jarClassLoader (which is,
    // by design, mutable).  

    // Since plugins are making use of it, we're going to make sure we use Spark's 
    // classloader as the canonical classloader moving forward.
    Thread.currentThread().setContextClassLoader(session.sharedState.jarClassLoader)
    Vizier.mainClassLoader = session.sharedState.jarClassLoader

    return session
  }

  def initPlugins(sparkSession: SparkSession): SparkSession =
  {
    var spark: SparkSession = sparkSession
    //Set credential providers for tests that load from s3
    spark.conf.set("fs.s3a.aws.credentials.provider", 
        "com.amazonaws.auth.EnvironmentVariableCredentialsProvider,"+
        "org.apache.hadoop.fs.s3a.SharedInstanceProfileCredentialsProvider,"+
        "org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider")
    // SedonaSQLRegistrator.registerAll(spark)
    // SedonaVizRegistrator.registerAll(spark)
    System.setProperty("geospark.global.charset", "utf8")
    Caveats.registerAllUDFs(spark)
    UDTRegistration.register(classOf[BufferedImage].getName, classOf[ImageUDT].getName)

    spark.udf.register("vector_to_array", vectorToArrayUdf)
    spark.udf.register("array_to_vector", arrayToVectorUdf)

    return spark
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
