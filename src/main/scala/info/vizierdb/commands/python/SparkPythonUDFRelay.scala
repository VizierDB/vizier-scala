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
      Artifact.getOption(name.toLong) 
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