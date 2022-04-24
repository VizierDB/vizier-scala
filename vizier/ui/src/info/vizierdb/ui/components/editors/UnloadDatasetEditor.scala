package info.vizierdb.ui.components.editors

import rx._
import org.scalajs.dom
import info.vizierdb.ui.components._
import info.vizierdb.util.Logging
import info.vizierdb.serialized.CommandArgument
import scalatags.JsDom.all._
import info.vizierdb.types._
import info.vizierdb.ui.components.EnumerableParameter
import info.vizierdb.ui.rxExtras.implicits._
import play.api.libs.json._
import info.vizierdb.serialized.CommandArgumentList
import info.vizierdb.serializers._

class UnloadDatasetEditor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "data",
  val commandId: String = "unload"
)(implicit owner: Ctx.Owner) 
  extends ModuleEditor
  with Logging
{

  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    for( arg <- arguments )
    {
      arg.id match {
        case "dataset" => dataset.set(arg.value)
        case "unloadFormat" => format.value = arg.value.as[String]; activeFormat() = format.value
        case "unloadOptions" => 
          sparkOptions.set(
            arg.value.as[Seq[JsValue]]
               .map { CommandArgumentList.decodeAsMap(_) }
               .filter { x => 
                  x.get("unloadOptionKey").map {
                    case JsString("name") => 
                      publishName.set(x("unloadOptionValue"))
                      false
                    case _ => true
                  }.getOrElse { true }
                }
          )
        case "url" => publishUrl.set(arg.value)
      }
    }
  }

  override def currentState: Seq[CommandArgument] = 
  {
    Seq(
      dataset.toArgument,
      CommandArgument("unloadFormat", JsString(format.value)),
      CommandArgument(sparkOptions.id, 
        JsArray(
          sparkOptions.rawValue ++ (
            (format.value match { 
              case "publish_local" => 
                Seq("name" -> publishName.value)
              case _ => Seq.empty
            }).map { case (key, value) => 
              Json.arr(
                CommandArgument("unloadOptionKey", JsString(key)),
                CommandArgument("unloadOptionValue", value)
              )
            }
          )
        )
      ),
      format.value match {
        case "publish_local" => CommandArgument(publishUrl.id, JsString(""))
        case _ => publishUrl.toArgument
      }
    )
  }

  val dataset = 
    new ArtifactParameter(
      id = "dataset",
      name = "Dataset: ",
      artifactType = ArtifactType.DATASET,
      artifacts = delegate.visibleArtifacts
                          .flatMap { x => x.map { _.mapValues { _._1.t } } },
      required = true,
      hidden = false,
    )

  val format: dom.html.Select = 
    select(
      name := "file_format",
      DatasetFormat.ALL.zipWithIndex.map { 
        case ((_, "publish_local"), _)  => option(value := "publish_local", "Publish to Another Project")
        case ((label, id), 0) => option(value := id, label, selected)
        case ((label, id), _) => option(value := id, label)
      },
      onchange := { _:dom.Event => activeFormat() = format.value }
    ).render

  val activeFormat = Var[String](DatasetFormat.ALL.head._2)

  val sparkOptions: ListParameter = 
    new ListParameter("unloadOptions",
      "Spark Load Options",
      Seq[String]("Key", "Value"),
      Seq[() => Parameter](
        { () => new StringParameter(
                  "unloadOptionKey",
                  "Key",
                  true,
                  false
                ) },
        { () => new StringParameter(
                  "unloadOptionValue",
                  "Value",
                  true,
                  false
                ) },
      ),
      false,
      false
    )

  val publishName = 
    new StringParameter(
      id = "name", 
      name = "Name: ", 
      required = true, 
      hidden = false, 
      initialPlaceholder = "public name",
    )
  dataset.selectedDataset.triggerLater { _ match {
    case None => publishName.setHint("") 
    case Some(ds) => publishName.setHint(ds)
  }}

  val publishUrl =
    new StringParameter(
      id = "url",
      name = "URL: ",
      required = true,
      hidden = false,
      initialPlaceholder = "https://url/to/publish"
    )

  override val editorFields: Frag =
    div(`class` := "module editable unload_dataset",
      dataset.root,
      div(`class` := "format_field", 
        label("Format: ", `for` := "file_format"),
        format
      ),
      Rx { 
        activeFormat() match {
          case "publish_local" => publishName.root
          case _ => publishUrl.root
        }
      }.reactive,
      sparkOptions.root
    )


}