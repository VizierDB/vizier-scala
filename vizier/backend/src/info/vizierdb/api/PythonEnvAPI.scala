package info.vizierdb.api

import info.vizierdb.serialized
import info.vizierdb.catalog.PythonVirtualEnvironment
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.response.ErrorResponse
import java.sql.SQLException
import info.vizierdb.VizierException
import info.vizierdb.Vizier

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
  ): serialized.PythonEnvironment = 
  {
    CatalogDB.withDBReadOnly { implicit s =>
      PythonVirtualEnvironment.getOption(env)
                              .getOrElse { ErrorResponse.noSuchEntity }
                              .serialize
    }
  }

  def ListEnvs(): Seq[String] =
  {
    CatalogDB.withDBReadOnly { implicit s =>
      PythonVirtualEnvironment.list
    }
  }

  def Create(
    env: String,
    spec: serialized.PythonEnvironment
  ): Boolean = 
  {
    checkServerMode()
    val environment = 
      try {
        CatalogDB.withDB { implicit s => 
          PythonVirtualEnvironment.make(env, spec.version)
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