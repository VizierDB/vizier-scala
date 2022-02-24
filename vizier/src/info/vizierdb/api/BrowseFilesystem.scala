package info.vizierdb.api

import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.VizierException
import info.vizierdb.Vizier
import info.vizierdb.util.FileUtils
import info.vizierdb.types.MIME
import java.io.File

object BrowseFilesystem
{
  def apply(path: String = ""): Seq[FilesystemObject] = 
  {
    if(Vizier.config.serverMode()){ 
      throw new VizierException("Filesystem browsing not supported in server mode")
    }
    val pathElements = 
      if(path == ""){ Seq.empty }
      else { path.split("/").toSeq }

    var currDir = Vizier.config.workingDirectoryFile
    val wd = currDir.toPath

    if(!currDir.isDirectory){
      throw new NoSuchElementException(s"$path is not a directory")
    }
    for(element <- pathElements){
      currDir = new File(currDir, element)
      if(!currDir.exists){
        throw new NoSuchElementException(s"${currDir.toString()} does not exist")
      }
      if(!currDir.isDirectory){
        throw new NoSuchElementException(s"${currDir.toString()} is not a directory")
      }
    }

    currDir.listFiles
           .toSeq
           .filterNot { _.getName.startsWith(".") }
           .map { element =>
              val relative = wd.relativize(element.toPath).toString
              FilesystemObject(
                mimeType = 
                  if(element.isDirectory) { MIME.DIRECTORY }
                  else { FileUtils.guessMimeType(element) },
                label = element.getName(),
                internalPath = relative,
                externalPath = relative,
                hasChildren = element.isDirectory
              )
           }
  }
}