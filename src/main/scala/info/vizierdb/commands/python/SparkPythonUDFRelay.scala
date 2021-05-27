package info.vizierdb.commands.python

import scalikejdbc.DB
import org.mimirdb.blobs.BlobStore
import info.vizierdb.catalog.Artifact
import info.vizierdb.types._
import org.mimirdb.data.MetadataMap
import org.mimirdb.api.MimirAPI

object SparkPythonUDFRelay
  extends BlobStore(MimirAPI.metadata, null)
{
  override val blobs: MetadataMap = null
  override def put(name: String, blobType: String, data: Array[Byte])
    { throw new Exception("Blob-Put Unsupported") }

  override def get(name: String): Option[(String, Array[Byte])] =
    { throw new Exception("Blob-Get Unsupported") }

  override def getPickle(name: String): Option[Array[Byte]] =
  {
    DB.readOnly { implicit s => 
      Artifact.lookup(name.toLong) 
    }.map { artifact: Artifact => 
      if( (artifact.t != ArtifactType.FUNCTION)
          || (artifact.mimeType != MIME.PYTHON))
      {
        throw new Exception("Not a python function")
      }
      udfCache.getOrElseUpdate(
        name, 
        PythonProcess.udfBuilder.pickle(artifact.string)
      )
    }
  }
}