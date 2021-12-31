package info.vizierdb.filestore

import java.io.{ InputStream, OutputStream, FileOutputStream }
import java.net.{ URL, URI }
import org.apache.spark.sql.DataFrame
import info.vizierdb.commands.FileArgument
import info.vizierdb.types._
import info.vizierdb.Vizier

object Staging
{
  val safeForRawStaging = Set(
    DatasetFormat.CSV ,
    DatasetFormat.JSON,
    DatasetFormat.Excel,
    DatasetFormat.XML,
    DatasetFormat.Text
  )
  
  val stagingExemptProtocols = Set(
    DataSourceProtocol.S3A    
  )

  val stagingDefaultsToRelative = true

  val bulkStorageFormat = "parquet"

  /**
   * Transfer an InputStream to an OutputStream
   * @param input     The InputStream to read from
   * @param output    The OutputStream to write to
   *
   * Obsoleted in Java 9 with InputStream.transferTo... but we're on 8 for now
   */
  private def transferBytes(input: InputStream, output: OutputStream): Unit =
  {
    val buffer = Array.ofDim[Byte](1024*1024) // 1MB buffer
    var bytesRead = input.read(buffer)
    while(bytesRead >= 0) { 
      output.write(buffer, 0, bytesRead)
      bytesRead = input.read(buffer)
    }
  }

  def stage(input: InputStream, projectId: Identifier, artifactId: Identifier): Unit =
  {
    val file = Filestore.getRelative(projectId, artifactId)
    transferBytes(input, new FileOutputStream(file))
  }
  def stage(url: URL, projectId: Identifier, artifactId: Identifier): Unit =
    stage(url.openStream(), projectId, artifactId)

  def stage(input: DataFrame, format: String, projectId: Identifier, artifactId: Identifier): Unit =
  {
    val file = Filestore.getRelative(projectId, artifactId)
    input.write
         .format(format)
         .save(file.toString)
  }

  /**
   * Stage the provided URL into the local filesystem if necessary
   * 
   * @param url           The URL to stage into the local filesystem
   * @param sparkOptions  Options to provide to the LoadConstructor
   * @param format        The format to stage the provided file as (if needed)
   * @param tableName     The name of the table to associate with the file
   * @return              A four tuple of: (i) The staged URL, (ii) the sparkOptions
   *                      parameter passed, (iii) The format used to stage the file
   *                      and (iv) Whether the returned url is relative to the data
   *                      directory.
   *
   * This function takes a URL and rewrites it to allow efficient local access using
   * the Catalog's defined staging provider.  Generally, this means copying the URL
   * into the local filesystem.
   * 
   * The behavior of this function is governed by the type of URL and the provided
   * format.  Some URL protocols are exempt from staging, e.g., s3a links, since
   * spark interacts with these directly (and presumably efficiently).  Apart from
   * that the staging can be handled either "raw" or via spark depending on the
   * format.  For example, a CSV file can be downloaded and its raw bytestream
   * can just be saved on disk.  Other formats like google sheets require a level
   * of indirection, using the corresponding spark dataloader to bring the file into
   * spark and then dumping it out in e.g., parquet format.
   */
  def stage(
    url: String, 
    sparkOptions: Map[String,String], 
    format: String, 
    projectId: Identifier,
    allocateArtifactId: () => Identifier
  ): (FileArgument, Map[String,String], String) =
  {
    if(stagingExemptProtocols(URI.create(url).getScheme)){
      return ( 
        FileArgument(url = Some(url)),
        sparkOptions,
        format
      )
    } else if(safeForRawStaging(format)){
      val artifactId = allocateArtifactId()
      stage(new URL(url), projectId, artifactId)
      return ( 
        FileArgument(fileid = Some(artifactId)),
        sparkOptions,
        format,
      )
    } else {
      val artifactId = allocateArtifactId()
      var parser = Vizier.sparkSession.read.format(format)
      for((option, value) <- sparkOptions){
        parser = parser.option(option, value)
      }
      stage(
        parser.load(url),
        bulkStorageFormat,
        projectId = projectId,
        artifactId = artifactId
      )
      return (
        FileArgument(fileid = Some(artifactId)),
        Map(),
        bulkStorageFormat
      )
    }
  }
}
