package info.vizierdb

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import java.io.File
import info.vizierdb.util.ClassLoaderUtils

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

    val df = Vizier.sparkSession.sql("""
      SELECT gaussian(5.0, 1.0)
    """)

    ok
  }

}