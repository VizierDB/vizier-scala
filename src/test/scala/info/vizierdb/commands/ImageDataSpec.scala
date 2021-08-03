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
package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.apache.spark.sql.types._

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Message, DatasetMessage }
import info.vizierdb.MutableProject
import java.awt.image.BufferedImage

class ImageDataSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "load and display images" >> 
  {
    val project = MutableProject("Load Images Test")

    project.script("""
      |ds = vizierdb.new_dataset()
      |
      |ds.insert_column("url", "string")
      |ds.insert_column("image", "image/png")
      |
      |url = "test_data/test_image.png"
      |with open(url, "rb") as f:
      |  data = f.read()
      |
      |ds.insert_row([url, data])
      |ds.save("image_data", use_deltas = False)
      |ds.show()
    """.stripMargin)
    project.waitUntilReadyAndThrowOnError

    val outputs = project.lastOutput

    outputs must contain { (x:Message) => x.mimeType.equals(MIME.DATASET_VIEW) }
    val dsMessage = outputs.filter { (x:Message) => x.mimeType.equals(MIME.DATASET_VIEW) }
                           .head
    // println(s"MESSAGE: ${dsMessage.dataString}")
    val dataset = dsMessage.dataJson.as[DatasetMessage]

    dataset.dataCache must beSome
    
    dataset.dataCache.get.schema must containTheSameElementsAs(Seq(
      StructField("url", StringType),
      StructField("image", ImageUDT)
    ))

    val firstRow = dataset.dataCache.get.data(0)
    val imageValue = firstRow(1)
    // println(imageValue)
    imageValue must not beNull


    // println(dataset.dataCache.get.schema)
    // println(firstRow)
  }
}