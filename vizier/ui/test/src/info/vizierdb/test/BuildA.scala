package info.vizierdb.test

import scala.scalajs.js
import scala.scalajs.js.JSON
import info.vizierdb.ui.network._
import info.vizierdb.types._


object BuildA
{
  def Package(
    id: String,
    category: String = null,
    name: String = null
  )(
    commands: CommandDescriptor*
  ): PackageDescriptor =  
    js.Dictionary(
      "category" -> Option(category).getOrElse { id },
      "id" -> id,
      "name" -> Option(name).getOrElse { id },
      "commands" -> js.Array(commands:_*)
    ).asInstanceOf[PackageDescriptor]

  def Command(
    id: String,
    name: String = null,
    suggest: Boolean = false
  )(
    parameters: js.Dictionary[Any]*
  ): CommandDescriptor =  
    js.Dictionary(
      "id" -> id,
      "name" -> Option(name).getOrElse { id },
      "suggest" -> suggest,
      "parameters" -> 
        js.Array(
          parameters.zipWithIndex.map { case (p, idx) => 
            p("index") = idx
            p.asInstanceOf[ParameterDescriptor]
          }:_*
        )
    ).asInstanceOf[CommandDescriptor]

  def Parameter(
    id: String,
    datatype: String,
    name: String = null
  ): js.Dictionary[Any] = 
    js.Dictionary(
      "id" -> id,
      "name" -> Option(name).getOrElse { id },
      "datatype" -> datatype,
      "hidden" -> false,
      "required" -> false
    )

  var nextModuleId = -1l
  def getNextModuleId = { nextModuleId += 1; nextModuleId.toString() }

  var nextArtifactId = -1l
  def getNextArtifactId = { nextArtifactId += 1; nextArtifactId.toString() }

  def Module(
    packageId: String,
    commandId: String,
    artifacts: Seq[(String, ArtifactType.T)] = Seq.empty,
    id: String = getNextModuleId,
    state: ExecutionState.T = ExecutionState.DONE
  )(
    arguments: (String, Any)*
  ) = 
    js.Dictionary(
      "id" -> id,
      "state" -> -1,
      "statev2" -> state.id,
      "command" -> js.Dictionary(
        "packageId" -> packageId,
        "commandId" -> commandId,
        "arguments" -> js.Array(arguments.map { case (id, value) => 
          js.Dictionary(
            "id" -> id, "value" -> value
          ).asInstanceOf[CommandArgument]
        }:_*)
      ).asInstanceOf[ModuleCommand],
      "text" -> s"$packageId.$commandId",
      "links" -> js.Dictionary(),
      "outputs" -> js.Dictionary(
        "stdout" -> js.Array(),
        "stderr" -> js.Array()
      ),
      "artifacts" -> js.Array(artifacts.map { case (name, t) =>
        js.Dictionary(
          "id" -> getNextArtifactId,
          "name" -> name,
          "category" -> t.toString,
          "objType" -> "dataset/view"
        ).asInstanceOf[ArtifactSummary]
      }:_*)
    )
}