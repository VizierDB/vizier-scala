package info.vizierdb.api

import info.vizierdb.serialized
import info.vizierdb.catalog.PythonVirtualEnvironment
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.response.ErrorResponse

object PythonEnvAPI
{
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

  def Create(
    env: String,
    spec: serialized.PythonEnvironment
  ): Boolean = 
  {
    val environment = 
      CatalogDB.withDBReadOnly { implicit s => 
        PythonVirtualEnvironment.make(env, spec.version)
      }
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