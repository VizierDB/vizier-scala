package info.vizierdb

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import java.io.File
import info.vizierdb.util.ClassLoaderUtils
import org.apache.spark.sql.functions.udf

class PluginTest extends Specification with BeforeAll
{
  def beforeAll(): Unit = 
  {
    SharedTestResources.init()
  }

  "Load Class Jars" >>
  {
    val jar = new File("plugins/mimir-pip.jar")

    val plugin = Plugin.load(jar)

    ClassLoaderUtils.withContextClassLoader(Vizier.mainClassLoader) {
      val df = Vizier.sparkSession.sql("""
        SELECT gaussian(cast(5.0 as double), cast(1.0 as double)) as variable
      """)
      df.show()
    } 

    ok
  }

}