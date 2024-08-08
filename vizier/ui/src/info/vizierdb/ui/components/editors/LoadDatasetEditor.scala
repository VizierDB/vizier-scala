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
package info.vizierdb.ui.components.editors

import info.vizierdb.ui.components._
import info.vizierdb.serialized.{ CommandArgument, CommandArgumentList }
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.serialized.CommandDescription
import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.types.MIME
import info.vizierdb.ui.Vizier
import scala.util.Success
import scala.util.Failure
import info.vizierdb.util.Logging
import info.vizierdb.types.DatasetFormat
import play.api.libs.json._
import info.vizierdb.serializers._

class LoadDatasetEditor(
  val delegate: ModuleEditorDelegate,
  val packageId: String = "data",
  val commandId: String = "load"
)(implicit owner: Ctx.Owner) 
  extends ModuleEditor
  with Logging
{

  val urlField = input(`type` := "text", 
                       name := "url",
                       placeholder := "https://url/of/file.csv      OR      path/to/file.csv",
                       onchange := { _:dom.Event => urlChanged }).render
  val directoryStack = Var[List[FileBrowser]](Nil)
  val format = 
    select(
      name := "file_format",
      DatasetFormat.ALL.map { case (label, id) =>
        option(value := id, label)
      },
      onchange := { _:dom.Event => formatChanged }
    ).render:dom.html.Select

  val sparkOptions: ListParameter = 
    new ListParameter("loadOptions",
      "Spark Load Options",
      Seq[String]("Key", "Value"),
      { () => 
        Seq(
          new StringParameter(
            "loadOptionKey",
            "Key",
            true,
            false
          ),
          new StringParameter(
          "loadOptionValue",
          "Value",
          true,
          false
          ),
        )
      },
      false,
      false
    )
  val datasetName =
    new StringParameter(
      "name",
      "Dataset Name: ",
      false,
      false
    )
  val schema = new ListParameter("schema",
    "Schema (leave empty to guess)",
    Seq[String]("Column", "Data Type"),
    { () => Seq(
        new StringParameter(
          "schema_column",
          "Column",
          true,
          false
        ),
        new DataTypeParameter(
          "schema_datatype",
          "Data Type",
          true,
          false
        ),
      )
    },
    false,
    false
  )

  val optionalParameters = Seq[(Set[String], Parameter)](
    Set(DatasetFormat.CSV) ->
      new BooleanParameter("header", "File has Headers: ", true, false, true),
    Set(DatasetFormat.CSV) ->
      new StringParameter("delimiter", "Field Delimiter: ", true, false, ","),
    Set(DatasetFormat.CSV) ->
      new BooleanParameter("loadInferTypes", "Guess Schema: ", true, false, true),
    Set(DatasetFormat.JSON) ->
      new BooleanParameter("multiline", "Records Span Multiple Lines: ", true, false, true)
  )
  val directToSparkOptionalParameters = Set(
    "header",
    "delimiter",
    "multiline",
  )
  val optionalParameterByKey = 
    optionalParameters.map { param => param._2.id -> param._2 }
                      .toMap

  val activeParameters = Var(Seq[Parameter](sparkOptions))
  formatChanged

  override val editorFields: Frag = 
    div(`class` := "load_dataset",
      div(`class` := "header",
        // wrapper needed for flexbox
        div(`class` := "back_button_wrapper",
          Rx { 
            button(
              if(directoryStack().size <= 1) { disabled } 
              else { attr("ignored") := "ignored" },
              FontAwesome("arrow-left"),
              onclick := { _:dom.Event =>
                if(directoryStack.now.size > 1){
                  directoryStack() = directoryStack.now.tail
                  setURL(directoryStack.now.headOption
                                       .map { _.externalPath }
                                       .getOrElse { "" })
                }
              }
            )
          }.reactive
        ),
        div(`class` := "url", 
          label(`for` := "url", "File or URL: "),
          urlField
        ),
      ),
      div(`class` := "file_browser",
        Rx { 
          directoryStack().headOption match {
            case None => Spinner(30)
            case Some(browser) => browser.root
          }
        }.reactive
      ),
      div(`class` := "format_field", 
        label("Format: ", `for` := "file_format"),
        format
      ),
      Rx {
        div(
          activeParameters().map { param =>
            div(`class` := "format_field", param.root) 
          }
        )
      }.reactive,
      div(`class` := "schema", schema.root)
    )


  def setURL(path: String)
  {
    urlField.value = path
    urlChanged
  }

  def guessFormat: DatasetFormat.T =
  {
    val url = urlField.value

    // Special-case some URLs
    if(url startsWith (Vizier.api.baseUrl+"/published")) {
      return DatasetFormat.VizierLocal
    }

    // Fall back to the filename
    val file = url.split("/").last
    val components = file.split("\\.")
    return components.last match {
      case "json" => DatasetFormat.JSON
      case "csv"  => DatasetFormat.CSV
      case "tsv"  => DatasetFormat.CSV
      case "xlsx" => DatasetFormat.Excel
      case _      => DatasetFormat.Text
    }

  }

  def urlChanged: Unit =
  {
    datasetName.setHint(
      // File basename
      urlField.value.split("/").last.split("\\.").head
    )
    format.value = guessFormat
    formatChanged
  }

  def formatChanged: Unit =
  {
    // println(s"Format now ${format.value}")
    val fmt = format.value
    activeParameters() =
      Seq( datasetName ) ++
      optionalParameters.filter { _._1(fmt) }
                        .map { _._2 } ++
      Seq( sparkOptions )
    // println(s"Format done")
  }

  def visitDirectory(path: String = "", externalPath: String = "") =
  {
    Vizier.api
          .fsGet(path)
          .onComplete {
            case Success(files) => 
              directoryStack.update { new FileBrowser(files, externalPath) :: _ }
            case Failure(err) => 
              logger.error(s"${err.getClass.getSimpleName}: ${err.getMessage}")
          }
  }
  visitDirectory("")

  val GENERIC_IMAGE = "image/.*".r

  def ellipsize(str: String) =
    if(str.length() > 16){
      str.take(6)+"..."+str.takeRight(6)
    } else {
      str
    }

  case class FileBrowser(files: Seq[FilesystemObject], externalPath: String)
  {
    val root = 
      div(`class` := "file_list", 
        files.map { file => 
          div(`class` := "file",
            FontAwesome(
              file.icon.getOrElse {
                file.mimeType match {
                  case MIME.DIRECTORY => "folder-o"
                  case MIME.JAVASCRIPT => "file-code-o"
                  case GENERIC_IMAGE() => "file-image-o"
                  case _ => "file-o"
                }
              }
            ),
            span(`class` := "label", ellipsize(file.label)),
            if(file.hasChildren){
              onclick := { _:dom.Event => 
                setURL(file.externalPath)
                visitDirectory(file.internalPath, file.externalPath) 
              }
            } else {
              onclick := { _:dom.Event => 
                setURL(file.externalPath)
              }
            }
          )
        }
      ).render
  }

  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    print(s"loadState: ${arguments.mkString("\n")}")
    for(arg <- arguments){
      arg.id match {
        case "file" => 
          (arg.value \ "url").asOpt[String] match {
            case Some(f) => urlField.value = f
            case None    => println("ERROR: Internal files are not supported yet")
          }
        case "name"       => datasetName.set(arg.value)
        case "loadFormat" => format.value = arg.value.as[String]
        case "loadOptions" => 
          {
            val (special, generic) = 
              arg.value.as[Seq[CommandArgumentList.T]]
                       .map { CommandArgumentList.toMap(_) }
                       .partition { x => 
                          directToSparkOptionalParameters(
                            x.getOrElse("loadOptionKey", JsString("")).as[String]
                          )
                        }
            sparkOptions.set(generic)
            special.map { r => 
              val value = r("loadOptionValue")
              optionalParameterByKey(r("loadOptionKey").as[String]) match {
                case x:StringParameter => x.set(value)
                case x:BooleanParameter => 
                  x.set(JsBoolean(value.as[String].toLowerCase == "true"))
                case _ => ???
              }
                          
            }
          }
        case "loadDataSourceErrors" => ()
        case "schema" => println(s"Schema: ${arg.value}"); schema.set(arg.value)
        case x if optionalParameterByKey contains x => 
          optionalParameterByKey(x).set(arg.value)
        case x => println(s"ERROR: Unsupported load editor parameter $x")
      }
    }
  }



  override def currentState: Seq[CommandArgument] = 
  {
    Seq(
      CommandArgument(
        "file", Json.obj("url" -> JsString(urlField.value))
      ), 
      datasetName.toArgument,
      CommandArgument(
        "loadFormat" -> JsString(format.value)
      ),
      CommandArgument(
        "loadInferTypes" -> JsBoolean(true)
      ),
      schema.toArgument,
      CommandArgument(
        sparkOptions.id -> JsArray(
          sparkOptions.rawValue ++ 
          optionalParameters.filter { x => directToSparkOptionalParameters(x._2.id) }
                            .map { _._2 }
                            .map { x:Parameter => 
                              // println(s"Saving ${x.id} -> ${x.value}")
                              Json.arr(
                                CommandArgument("loadOptionKey", JsString(x.id)),
                                CommandArgument("loadOptionValue", 
                                  x match { 
                                    case _:StringParameter => x.value
                                    case b:BooleanParameter => 
                                      JsString(if(b.value.as[Boolean]) { "true" } else { "false" })
                                    case _ => ???
                                  }
                                )
                              )
                            }
                            .toSeq
        )
      )
    ) ++ optionalParameters.filter { x => !directToSparkOptionalParameters(x._2.id) }
                           .map { _._2.toArgument }
  }
}
