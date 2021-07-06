package info.vizierdb.test

import scala.scalajs.js
import scala.scalajs.js.JSON
import info.vizierdb.ui.network._
import info.vizierdb.types._
import info.vizierdb.serialized
import info.vizierdb.nativeTypes
import info.vizierdb.shared.HATEOAS

object BuildA
{
  def Package(
    id: String,
    category: String = null,
    name: String = null
  )(
    commands: serialized.PackageCommand*
  ): serialized.PackageDescription =  
    serialized.PackageDescription(
      category = Option(category).getOrElse { id },
      id = id,
      name = Option(name).getOrElse { id },
      commands = js.Array(commands:_*)
    )

  def Command(
    id: String,
    name: String = null,
    suggest: Boolean = false
  )(
    parameters: (Int => serialized.ParameterDescription)*
  ): serialized.PackageCommand =  
    serialized.PackageCommand(
      id = id,
      name = Option(name).getOrElse { id },
      suggest = Some(suggest),
      parameters = parameters.zipWithIndex.map { case (p, idx) => p(idx) }
    )

  var nextModuleId = -1l
  def getNextModuleId = { nextModuleId += 1; nextModuleId }

  var nextArtifactId = -1l
  def getNextArtifactId = { nextArtifactId += 1; nextArtifactId }

  def Module(
    packageId: String,
    commandId: String,
    artifacts: Seq[(String, ArtifactType.T)] = Seq.empty,
    id: Identifier = getNextModuleId,
    state: ExecutionState.T = ExecutionState.DONE
  )(
    arguments: (String, nativeTypes.JsValue)*
  ) = 
    serialized.ModuleDescription(
      id = id.toString,
      moduleId = id,
      state = -1,
      statev2 = state,
      command = serialized.CommandDescription(
        packageId = packageId,
        commandId = commandId,
        arguments = serialized.CommandArgumentList(arguments:_*)
      ),
      text = s"$packageId.$commandId",
      links = HATEOAS(),
      outputs = serialized.ModuleOutputDescription(
        stdout = Seq.empty,
        stderr = Seq.empty,
      ),
      timestamps = serialized.Timestamps(new js.Date()),
      datasets = Seq.empty, // Technically this should be a subset of the below, but we want to deprecate it
      charts = Seq.empty, // Technically this should be a subset of the below, but we want to deprecate it
      artifacts = artifacts.map { case (name, t) =>
        val id = getNextArtifactId
        serialized.StandardArtifact(
          id = id,
          key = id,
          name = name,
          category = t,
          objType = "dataset/view",
          links = HATEOAS()
        )
      },
      resultId = Some(1)
    )

  def WorkflowByInserting(
    workflow: serialized.WorkflowDescription, 
    position: Int, 
    module: serialized.ModuleDescription,
  ) = 
    workflow.copy(
      modules = workflow.modules.patch(position, Seq(module), 0)
    )

  def WorkflowByAppending(
    workflow: serialized.WorkflowDescription, 
    module: serialized.ModuleDescription,
  ) = 
    workflow.copy(
      modules = workflow.modules :+ module
    )
}