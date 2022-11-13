package info.vizierdb.api

import info.vizierdb.serialized
import info.vizierdb.catalog.PythonVirtualEnvironment
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.response.ErrorResponse
import java.sql.SQLException
import info.vizierdb.VizierException
import info.vizierdb.Vizier
import info.vizierdb.commands.python.PythonEnvironment

object PythonEnvAPI
{
  def checkServerMode() = 
    if(Vizier.config.serverMode()){
      ErrorResponse.invalidRequest(
        "This route is not available in server mode"
      )
    }

  def Get(
    env: String
  ): serialized.PythonEnvironmentDescriptor = 
  {
    PythonEnvironment.INTERNAL.get(env)
      .map { _.serialize }
      .orElse {
        CatalogDB.withDBReadOnly { implicit s =>
          PythonVirtualEnvironment.getOption(env)
                                  .map { _.serialize }
        }
      }
      .getOrElse { ErrorResponse.noSuchEntity }

  }

  def ListEnvs(): Map[String, serialized.PythonEnvironmentSummary] =
  {
    CatalogDB.withDBReadOnly { implicit s =>
      (
        PythonEnvironment.INTERNAL.mapValues { _.summary } ++ 
        PythonVirtualEnvironment.list
      ).toMap
    }
  }

  def Create(
    env: String,
    spec: serialized.PythonEnvironmentDescriptor
  ): Boolean = 
  {
    checkServerMode()
    if(PythonEnvironment.INTERNAL contains env){
      ErrorResponse.invalidRequest(
        s"$env is a reserved environment name used by the system"
      )
    }
    val environment = 
      try {
        CatalogDB.withDB { implicit s => 
          PythonVirtualEnvironment.make(env, spec.pythonVersion)
        }
      } catch {
        case t: SQLException => 
          ErrorResponse.invalidRequest(
            "Error creating the venv (another venv with the same name probably exists)"
          )
      }
    environment.init()
    for( pkgSpec <- spec.packages ) {
      environment.Environment.install(pkgSpec.name, pkgSpec.version)
    }
    CatalogDB.withDB { implicit s => 
      environment.save
    }
    true
  }

  def Delete(
    env: String,
  ): Boolean = 
  {
    checkServerMode()
    if(PythonEnvironment.INTERNAL contains env){
      ErrorResponse.invalidRequest(
        s"$env is a reserved environment name used by the system"
      )
    }
    val environment = 
      CatalogDB.withDBReadOnly { implicit s =>
        PythonVirtualEnvironment.getOption(env)
                                .getOrElse { ErrorResponse.noSuchEntity }
      }
    environment.delete()
    CatalogDB.withDB { implicit s =>
      environment.drop()
    }
    true
  }

  def ListPackages(
    env: String
  ): Seq[serialized.PythonPackage] = 
  {
    val environment = 
      CatalogDB.withDBReadOnly { implicit s =>
        PythonVirtualEnvironment.getOption(env)
                                .getOrElse { ErrorResponse.noSuchEntity }
      }
    environment.packages
  }

  def InstallPackage(
    env: String, 
    spec: serialized.PythonPackage
  ): Boolean = 
  {
    checkServerMode()
    val environment = 
      CatalogDB.withDBReadOnly { implicit s =>
        PythonVirtualEnvironment.getOption(env)
                                .getOrElse { ErrorResponse.noSuchEntity }
      }
    environment.Environment.install(spec.name, spec.version)
    CatalogDB.withDB { implicit s =>
      environment.save()
    }
    true
  }
}