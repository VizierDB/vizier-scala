package info.vizierdb.catalog.gc

import java.io.File
import scala.io.Source
import scala.util.hashing.MurmurHash3
import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.catalog.Artifact
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.util.TimerUtils
import scala.collection.mutable
import info.vizierdb.catalog.{
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
import org.mimirdb.api.MimirAPI

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
    command.replaceArguments(arguments){
      case (_:FileParameter, arg: JsObject)
        if(fileIdsToReplace.intersect(arg.as[FileArgument].fileid.toSet).size > 0)
          => Json.toJson(
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
    //   Mimir
    DB.autoCommit { implicit s => 
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

      for(m <- allModules){
        fixModuleFileArguments(
          canonicalArtifact.id, 
          artifactIdsToRename.toSet,
          m.command.get, 
          m.arguments
        ) match { 
          case None => ()
          case Some(newArgs) => {
            logger.trace(s"Updating File ID for $m")
            m.replaceArguments(newArgs)
          }
        }
      }
    }
    for(artifact <- artifactsToRename){
      MimirAPI.catalog.replaceFile(artifact.relativeFile.toString, canonicalArtifact.relativeFile.toString)
      MimirAPI.catalog.replaceFile(artifact.absoluteFile.toString, canonicalArtifact.relativeFile.toString)
    }
    for(file <- artifactsToRename){
      file.absoluteFile.delete()
    }
  }

  def dedup(projectId: Option[Identifier] = None): Unit = 
  {
    val files = 
      DB.readOnly { implicit s =>
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

  }

}
