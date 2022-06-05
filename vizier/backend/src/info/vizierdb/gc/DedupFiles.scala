package info.vizierdb.catalog.gc

import java.io.File
import scalikejdbc._
import scala.io.Source
import scala.util.hashing.MurmurHash3
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.TimerUtils
import scala.collection.mutable
import info.vizierdb.catalog.{
  CatalogDB,
  Module,
  ArtifactRef,
  InputArtifactRef,
  OutputArtifactRef
}
import play.api.libs.json._
import info.vizierdb.commands.Command
import info.vizierdb.commands.Parameter
import info.vizierdb.commands.FileArgument
import info.vizierdb.commands.FileParameter
import info.vizierdb.spark.DataFrameCache
import info.vizierdb.spark.LoadConstructor
import info.vizierdb.spark.load.LoadSparkCSV
import info.vizierdb.spark.load.LoadSparkDataset

object DedupFiles 
  extends LazyLogging
{
  def apply() = dedup(None)
  def apply(projectId: Identifier) = dedup(Some(projectId))

  def getFileHash(file: File): Int =
  {
    MurmurHash3.orderedHash(
      Source.fromFile(file).getLines()
    )
  }

  def filesAreIdentical(file1: File, file2: File): Boolean = 
  {
    TimerUtils.logTime(s"Compare $file1 vs $file2", log = logger.trace(_)) {
      val data1 = Source.fromFile(file1)
      val data2 = Source.fromFile(file2)

      while( data1.hasNext && data2.hasNext )
      {
        if(data1.next() != data2.next()) { return false }
      }
      if(data1.hasNext){ return false } // len(file1) > len(file2)
      if(data2.hasNext){ return false } // len(file1) < len(file2)
      return true
    }
  }

  def fixModuleFileArguments(
    canonicalFileId: Identifier,
    fileIdsToReplace: Set[Identifier],
    command: Command,
    arguments: JsObject
  ): Option[JsObject] =
  {
    logger.trace(s"Fixing File {${fileIdsToReplace.mkString(", ")}} -> $canonicalFileId in ${command.name}($arguments)")
    command.replaceArguments(arguments){
      case (_:FileParameter, arg: JsObject)
        if(fileIdsToReplace.intersect(arg.as[FileArgument].fileid.toSet).size > 0) => 
            logger.debug(s"Found matching file parameter in $arg; Replacing id ${arg.as[FileArgument].fileid.get} with $canonicalFileId")
            Json.toJson(
               arg.as[FileArgument].copy(fileid = Some(canonicalFileId))
             )
    }.map { _.as[JsObject] }
  }
  
  def coalesceFileArtifacts(artifacts: Seq[Artifact])
  {
    logger.debug(s"Coalescing : ${artifacts.map { _.id }.mkString(", ")}")
    if(artifacts.size <= 1){ return; }

    // Arbitrarily select the file with the lowest id to preserve
    val sorted = artifacts.sortBy { _.id }
    val canonicalArtifact = sorted.head

    // Coalesce the rest, replacing them with [[target]]
    val artifactsToRename = sorted.tail
    val artifactIdsToRename = artifactsToRename.map { _.id }

    // File artifacts arise in the following contexts: 
    //   InputArtifactRef
    //   OutputArtifactRef
    //   FileArgument (in modules)
    //   Artifacts
    CatalogDB.withDB { implicit s => 
      withSQL { 
        val a = OutputArtifactRef.column
        update(OutputArtifactRef)
          .set(
            a.artifactId -> canonicalArtifact.id
          ).where.in(
            a.artifactId, artifactIdsToRename
          ) 
      }.update.apply()
      withSQL { 
        val a = InputArtifactRef.column
        update(InputArtifactRef)
          .set(
            a.artifactId -> canonicalArtifact.id
          ).where.in(
            a.artifactId, artifactIdsToRename
          ) 
      }.update.apply()

      val allModules = 
        withSQL {
          val m = Module.syntax
          select
            .from(Module as m)
        }.map { Module(_) }.list.apply()

      for(module <- allModules){
        fixModuleFileArguments(
          canonicalArtifact.id, 
          artifactIdsToRename.toSet,
          module.command.get, 
          module.arguments
        ) match { 
          case None => ()
          case Some(newArgs) => {
            logger.trace(s"Updating File ID\n   from: $module \n   to ${module.copy(arguments = newArgs)}")
            val m = Module.column
            withSQL {
              update(Module)
                .set(m.arguments -> newArgs.toString)
                .where.eq(m.id, module.id)
            }.update.apply()
          }
        }
      }

      withSQL { 
        val a = Artifact.column
        deleteFrom(Artifact)
          .where.in(
            a.id, artifactIdsToRename
          ) 
      }.update.apply()

      val allDatasets = 
        withSQL {
          val a = Artifact.syntax
          select
            .from(Artifact as a)
            .where.eq(a.t, ArtifactType.DATASET.id)
        }.map { Artifact(_) }.list.apply()

      for(a <- allDatasets){
        val dataset = a.datasetDescriptor
        dataset.constructor match {
          case l@LoadConstructor(f@FileArgument(Some(fileid), _, _, _),_,_,_,_,_,_) 
            if(artifactIdsToRename.contains(fileid)) =>
            a.replaceData(Json.toJson(
              dataset.copy(
                parameters = Json.toJson(
                  l.copy(
                    url = f.copy(fileid = Some(canonicalArtifact.id))
                  )
                ).as[JsObject]
              )
            ))
          case l@LoadSparkCSV(f@FileArgument(Some(fileid), _, _, _),_, _, _, _, _)
            if(artifactIdsToRename.contains(fileid)) =>
            a.replaceData(Json.toJson(
              dataset.copy(
                parameters = Json.toJson(
                  l.copy(
                    url = f.copy(fileid = Some(canonicalArtifact.id))
                  )
                ).as[JsObject]
              )
            ))
          case l@LoadSparkDataset(f@FileArgument(Some(fileid), _, _, _),_, _, _, _)
            if(artifactIdsToRename.contains(fileid)) =>
            a.replaceData(Json.toJson(
              dataset.copy(
                parameters = Json.toJson(
                  l.copy(
                    url = f.copy(fileid = Some(canonicalArtifact.id))
                  )
                ).as[JsObject]
              )
            ))

          case _ => /* only update matching constructors */
        }
      }


    }
    for(file <- artifactsToRename){
      file.absoluteFile.delete()
    }
  }

  def dedup(projectId: Option[Identifier] = None): Unit = 
  {
    val files = 
      CatalogDB.withDBReadOnly { implicit s =>
        Artifact.all(projectId)
                .filter { _.t == ArtifactType.FILE }
                .toSeq
      }

    logger.debug(s"Checking ${files.size} files for duplicates")

    val filesGroupedByHash = 
      TimerUtils.logTime("Compute File Hashes", log = logger.debug(_)) 
      { 
        files.groupBy { f => getFileHash(f.absoluteFile) }
      }

    for( (hash, candidateFileGroup) <- filesGroupedByHash )
    {
      logger.trace(s"Processing Hash $hash:\n${
        candidateFileGroup.map { "---> "+_.relativeFile }.mkString("\n")
      }")

      // Skip any file (group) that's already unique
      if(candidateFileGroup.size > 1){

        // Subgroup [[candidateFileGroup]] by checking for file equivalence.  In general, this 
        // should only ever produce one group (unless we have a hash collision)

        val identicalFileGroups = new mutable.ArrayBuffer[mutable.ArrayBuffer[Artifact]]()
        for(file <- candidateFileGroup){
          val idx = identicalFileGroups.indexWhere{ cmp => 
              filesAreIdentical(cmp.head.absoluteFile, file.absoluteFile)
            }
          if(idx >= 0){
            identicalFileGroups(idx).append(file)
          } else {
            identicalFileGroups.append(mutable.ArrayBuffer(file))
          }
        }

        for( fileGroup <- identicalFileGroups ){
          if(fileGroup.size > 1) { coalesceFileArtifacts(fileGroup) }
        }
      }
    }
    DataFrameCache.invalidate()

  }

}
