/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.ui.components.snippets


object ScalaSnippets extends SnippetsBase
{
  AddGroup("desktop", "Read")(
    "Get Spark Dataframe" -> 
      """// This returns a read-only spark dataframe
        |val df = vizierdb.dataframe("DATASET_NAME")""".stripMargin,

    "Get Parameter" ->
      """// This retrieves a string parameter.  Change the type as needed
        |val param = vizierdb.parameter[String]("PARAMETER_NAME")""".stripMargin,

    "Read File" ->
      """// This retrieves the contents of a file as a [scala.io.Source].
        |vizierdb.file("FILE_ARTIFACT_NAME") { source: scala.io.Source =>
        |  // The source will be automatically closed at the end of the block
        |}""".stripMargin,

    "Get SparkML Pipeline" ->
      """// Retrieve the ScalaML pipeline used to create the specified
        |// dataset.  Note that this will only work if the dataset was
        |// created with the [[vizierdb.pipeline]] method.
        |val pipeline = vizierdb.pipeline("PIPELINE_NAME")""".stripMargin,
  )

  AddGroup("plus", "Create")(
    "Save Spark Dataframe" ->
      """// This saves the spark dataframe in parquet format and makes it
        |// available to subsequent cells.  Note that, at present, caveats
        |// will not be propagated.
        |vizierdb.outputDataframe("DATASET_NAME", df)""".stripMargin,

    "Set Parameter" ->
      """//The provided parameter is saved.  The type is any Spark datatype
        |vizierdb.setParameter("PARAMETER_NAME", "VALUE", StringType)""".stripMargin,

    "Output File" ->
      """//Write out a file artifact
        |vizierdb.outputFile(
        |  name, 
        |  /* mimeType = "text/plain" */
        |) { f: java.io.OutputStream =>
        |  // write the file contents to `f`
        |}""".stripMargin,
  )

  AddGroup("edit", "Update")(
    "Apply SparkML Pipeline" ->
      """// Build and apply a SparkML pipeline to the specified dataframe
        |vizierdb.createPipeline("DATASET_NAME", /* OPTIONAL_OUTPUT_NAME */)(
        |  // A list of SparkML Transformers, separated by commas
        |)""".stripMargin,

    "Delete Artifact" ->
      """// Delete an artifact
        |vizierdb.delete("DATASET_NAME")""".stripMargin,
  )

  AddGroup("comment-o", "Message")(
    "Error Message" -> 
      """vizierdb.error("THE_MESSAGE")""".stripMargin,

    "HTML Message" ->
      """vizierdb.displayHTML(
        |  "THE_HTML_TEXT",
        |  // optionally provide javascript to run when the
        |  // message is displayed
        |  /* javascript = "", */
        |)""".stripMargin,

    "Display Dataset" ->
      """vizierdb.displayDataset(
        |  "DATASET_NAME", 
        |  // Optionally start the display at a specified offset
        |  /* offset = 0l, */
        |  /* limit = 100, */
        |)""".stripMargin,
  )

}


