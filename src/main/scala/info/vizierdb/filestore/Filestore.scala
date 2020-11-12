package info.vizierdb.filestore

import scalikejdbc._
import info.vizierdb.catalog.Artifact
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.types._

object Filestore
{
  val THRESHOLD_FRESH_FILE_FAILURES = 1000

  lazy val path = { val d = new File(Vizier.basePath, "files"); if(!d.exists()){ d.mkdir() }; d }

  def freshFile: (File, FileIdentifier) =
  {
    for(i <- 0 until THRESHOLD_FRESH_FILE_FAILURES){
      val element = java.util.UUID.randomUUID()
      val file = new File(path, element.toString)
      if(!file.exists()){ 
        return (file, element)
      }
    }
    throw new RuntimeException("Too many failures attempting to allocate a fresh file")
  }
}