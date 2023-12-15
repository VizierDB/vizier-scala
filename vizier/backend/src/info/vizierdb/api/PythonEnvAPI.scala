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
package info.vizierdb.api

import info.vizierdb.serialized
import info.vizierdb.catalog.PythonVirtualEnvironment
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.api.response.ErrorResponse
import java.sql.SQLException
import info.vizierdb.VizierException
import info.vizierdb.Vizier
import info.vizierdb.commands.python.PythonEnvironment
import info.vizierdb.commands.python.Pyenv
import info.vizierdb.catalog.PythonVirtualEnvironmentRevision
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging

object PythonEnvAPI
  extends Object
  with LazyLogging
{
  def checkServerMode() = 
    if(Vizier.config.serverMode()){
      ErrorResponse.invalidRequest(
        "This route is not available in server mode"
      )
    }

  def GetByName(
    env: String
  ): serialized.PythonEnvironmentDescriptor = 
  {
    PythonEnvironment.INTERNAL_BY_NAME.get(env)
      .map { _.serialize }
      .orElse {
        CatalogDB.withDBReadOnly { implicit s =>
          PythonVirtualEnvironment.getByNameOption(env)
                                  .map { _.serialize }
        }
      }
      .getOrElse { ErrorResponse.noSuchEntity }

  }

  def Get(
    envId: Identifier
  ): serialized.PythonEnvironmentDescriptor = 
  {
    PythonEnvironment.INTERNAL_BY_ID.get(envId)
      .map { _.serialize }
      .orElse {
        CatalogDB.withDBReadOnly { implicit s =>
          PythonVirtualEnvironment.getByIdOption(envId)
                                  .map { _.serialize }
        }
      }
      .getOrElse { ErrorResponse.noSuchEntity }

  }

  def Summary(): serialized.PythonSettingsSummary =
  {
    serialized.PythonSettingsSummary(
      CatalogDB.withDBReadOnly { implicit s =>
        (
          PythonEnvironment.INTERNAL_BY_NAME.values.map { _.summary } ++ 
          PythonVirtualEnvironment.list
        ).toIndexedSeq 
        // This is your regular reminder that toIndexedSeq forces a 
        // materialization of the sequence so that it is usable past
        // the end of the withDB block.
      },
      Pyenv.versions
    )
  }

  def Create(
    env: String,
    spec: serialized.PythonEnvironmentDescriptor
  ): serialized.PythonEnvironmentDescriptor = 
  {
    checkServerMode()
    if(PythonEnvironment.INTERNAL_BY_NAME contains env)
    {
      ErrorResponse.invalidRequest(
        s"$env is a reserved environment name used by the system"
      )
    }
    if(CatalogDB.withDBReadOnly { implicit s => 
        PythonVirtualEnvironment.exists(env)
      })
    {
      ErrorResponse.invalidRequest(
        s"$env already exists"
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
      environment.save.serialize
    }    
  }

  def Update(
    envId: Identifier,
    spec: serialized.PythonEnvironmentDescriptor
  ): serialized.PythonEnvironmentDescriptor = 
  {
    checkServerMode()
    if(PythonEnvironment.INTERNAL_BY_ID contains envId){
      ErrorResponse.invalidRequest(
        s"$envId is a reserved environment identifier used by the system"
      )
    }
    val environment =
      CatalogDB.withDB { implicit s => 
        PythonVirtualEnvironment.getByIdOption(envId)
                                .getOrElse { ErrorResponse.noSuchEntity }
      }

    if(spec.pythonVersion != environment.pythonVersion)
    {
      ErrorResponse.invalidRequest(
        s"Can't update an environment to a different python version (from ${environment.pythonVersion} to ${spec.pythonVersion}.  Create a new environment instead."
      )
    }

    // Name -> Version
    val currentPackages:Map[String, String] = 
      environment.Environment.packages.toMap
    // Name -> Spec
    val targetPackages:Map[String, serialized.PythonPackage] = 
      spec.packages.map { p => (p.name -> p) }.toMap

    val toBeDeleted =
      currentPackages.keySet.filter { !targetPackages.contains(_) }
    val toBeInstalledOrUpgraded = 
      targetPackages.filter { 
        case (pkg, targetSpec) =>
          currentPackages.get(pkg).map {
            // if the package is already installed...
            case currentVersion =>
              // If we're asked to upgrade, always upgrade
              if(targetSpec.version.isEmpty) { true }
              // Otherwise up(down)grade if there's a version mismatch
              else if(targetSpec.version.get != currentVersion) { true }
              // Same package, same version, leave intact
              else { false }
            // Or if the package isn't installed, install it
          }.getOrElse { true }
      }

    logger.info(s"Updating python environment: ${environment.name}")
    if(!toBeDeleted.isEmpty){
      logger.info(s"Deleting packages: \n${toBeDeleted.mkString("\n")}")
    }
    if(!toBeInstalledOrUpgraded.isEmpty)
    {
      logger.info(s"Installing packages: \n${toBeInstalledOrUpgraded.mkString("\n")}")
    }

    try {
      for( pkg <- toBeDeleted ) {
        environment.Environment.delete(pkg)
      }
      for( (pkg, spec) <- toBeInstalledOrUpgraded ) {
        environment.Environment.install(
          packageName = pkg, 
          version = spec.version,
          upgrade = currentPackages.contains(pkg)
        )
      }
    } catch {
      case FormattedError(err) => 
        ErrorResponse.invalidRequest(err)
    }
    CatalogDB.withDB { implicit s => 
      environment.save.serialize
    }
  }

  def Delete(
    envId: Identifier,
  ): Boolean = 
  {
    checkServerMode()
    if(PythonEnvironment.INTERNAL_BY_ID contains envId){
      ErrorResponse.invalidRequest(
        s"$envId is a reserved environment identifier used by the system"
      )
    }
    val environment = 
      CatalogDB.withDBReadOnly { implicit s =>
        PythonVirtualEnvironment.getByIdOption(envId)
                                .getOrElse { ErrorResponse.noSuchEntity }
      }
    environment.delete()
    CatalogDB.withDB { implicit s =>
      environment.drop()
    }
    true
  }

  def ListPackages(
    envId: Identifier
  ): Seq[serialized.PythonPackage] = 
  {
    val revision =
      CatalogDB.withDBReadOnly { implicit s =>
        PythonVirtualEnvironmentRevision.getActiveOption(envId)
                                        .getOrElse { ErrorResponse.noSuchEntity }
      }
    revision.packages
  }
}