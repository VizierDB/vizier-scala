package info.vizierdb.api

import scalikejdbc._
import scala.collection.mutable
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.VizierException
import info.vizierdb.Vizier
import info.vizierdb.util.FileUtils
import info.vizierdb.types.MIME
import java.io.File
import java.nio.file.Path
import info.vizierdb.catalog.PublishedArtifact
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierAPI

object BrowseFilesystem
{
  trait Directory
  {
    def label: String
    def icon: Option[String]
    def list(elements: Seq[String]): Seq[FilesystemObject]
  }

  case class LocalFSDirectory(wd: Path, val label: String)
    extends Directory
  {
    def icon: Option[String] = Some("hdd-o")
    def list(elements: Seq[String]): Seq[FilesystemObject] =
    {
      var currDir = wd.toFile()
      var pathSoFar = ""
      for(element <- elements){
        if(!currDir.isDirectory){
          throw new NoSuchElementException(s"$pathSoFar is not a directory")
        }
        currDir = new File(currDir, element)
        pathSoFar = s"$pathSoFar$element/"
        if(!currDir.exists){
          throw new NoSuchElementException(s"$pathSoFar does not exist")
        }
      }
      if(!currDir.isDirectory){
        throw new NoSuchElementException(s"$pathSoFar is not a directory")
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

  case class PublishedArtifactDirectory(val label: String)
    extends Directory
  {
    def icon: Option[String] = Some("share-alt")
    def list(elements: Seq[String]): Seq[FilesystemObject] =
    {
      if(!elements.isEmpty){
        throw new NoSuchElementException(s"${elements.mkString("/")} is not a directory")
      }
      else {
        DB.readOnly { implicit s => 
          PublishedArtifact.list
            .map { case (artifactLabel, summary) =>
              FilesystemObject(
                mimeType = 
                  summary.t match {
                    case ArtifactType.DATASET => MIME.DATASET_VIEW
                    case ArtifactType.FILE => summary.mimeType
                    case _ => MIME.RAW
                  },
                label = artifactLabel,
                internalPath = 
                  artifactLabel,
                externalPath = 
                  VizierAPI.urls.publishedArtifact(artifactLabel).toString,
                hasChildren = false,
                icon = 
                  summary.t match {
                    case ArtifactType.DATASET => Some("table")
                    case _ => None
                  }
              )
            }
        }
      }
    }
  }

  val mountPoints = mutable.Map[String, Directory]()
  // Initialized by Vizier.scala during startup

  def apply(path: String = ""): Seq[FilesystemObject] = 
  {
    if(Vizier.config.serverMode()){ 
      throw new VizierException("Filesystem browsing not supported in server mode")
    }
    if(path == ""){ 
      mountPoints.map { case (path, dir) =>
        FilesystemObject(
          mimeType = MIME.DIRECTORY,
          label = dir.label,
          internalPath = path,
          externalPath = "",
          hasChildren = true,
          icon = dir.icon
        )
      }.toSeq
    } else {
      val pathElements = path.split("/").toSeq
      val dir = mountPoints.get(pathElements.head)
                           .getOrElse {
                             throw new NoSuchElementException(s"${pathElements.head} is not a directory")
                           }
      dir.list(pathElements.tail)
         .map { x => x.copy(internalPath = pathElements.head + "/" + x.internalPath) }
    }
  }

  def mountLocalFS(
    mountPoint: String = "local",
    fsPath: Path = Vizier.config.workingDirectoryFile.toPath(), 
    label: String = "Local Files"
  ) = 
  {
    mountPoints(mountPoint) = LocalFSDirectory(fsPath, label)
  }

  def mountPublishedArtifacts(
    mountPoint: String = "published",
    label: String = "Published"
  ) =
  {
    mountPoints(mountPoint) = PublishedArtifactDirectory(label)
  }
}