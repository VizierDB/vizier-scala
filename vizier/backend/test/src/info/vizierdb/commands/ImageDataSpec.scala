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
import info.vizierdb.spark.udt.ImageUDT
import info.vizierdb.spark.SparkPrimitive

class ImageDataSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  val IMAGE = "iVBORw0KGgoAAAANSUhEUgAAAUAAAAFACAMAAAD6TlWYAAAABGdBTUEAALGPC/xhBQAAACBjSFJNAAB6JgAAgIQAAPoAAACA6AAAdTAAAOpgAAA6mAAAF3CculE8AAABIFBMVEX+AACmpv5TU///Ly//a2tOTv6TlP7+LCv/cXBra/+2tv7Z2f/+FBP/09Rqav58fP7+fHz+IiJHR///YWD+QECbm/8CA/7/AwL+PDwBAf+gof7/Cwv/Q0IvL/9LTP+Yl///sbEHB/8xMv7/aWoGBv7/JST+Xl7/ensAAP7/AQH/DxD/hYT+Dg3+ZGQ3Nv6Ulf//ISALC///ERH/Jyj+JyX/S0z/BQb/DQz+oKD/iYn/GRkdHP7/Fxf/ICH/XF3/Cgn+Njb/VVX+EhI6Of/+Ghn+Bgb/KSn/vb42Nf//p6aQkP5/fv6EhP4/P/+rrP7X1v7+8vIFBP8QEP7b2//+cnFtbf8gH/8EA/9nZv9iYv4JCf8REf8WF/8iIv5PT//////Ick2eAAAAAWJLR0Rfc9FRLQAAAAlwSFlzAAALEgAACxIB0t1+/AAAAAd0SU1FB+UHFg8IFeNX3RwAAALNSURBVHja7dzH0lRVGIZRUBHFHDEnTBgwZwyYEVGMqKj3fxnOn8lf1FsOumqt8e7d+3vOGZ3q08eOAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/m+NxQ9wYN8WJuDlOxi1xa5yK2+L2uCPujLvi7rgn7o374v4QUEABBRRQQAEFFFBAAQUUUEABBRRQwEMK+EA8GKfjoXg4HolH47F4PJ6IJ6PneSqejmfiTHS/Z+O5eD4EFFBAAQUUUEABBRRQQAEFFFBAAQUU8JACvhAvxtl4Kbr+5XglXo1z0Qv4WnTg1+ONaMDeAN2v3/9mvBUCCiiggAIKKKCAAgoooIACCiiggAIKeEgB346jAvYBade/E+/Ge/F+dIAPouf5MD6KBjwfRwXsvB+HgAIKKKCAAgoooIACCiiggAIKKKCAAh5SwE+iQT6NHrjr+4L2Z9EHkg3UAS5EX7j+PPrC9BfxZXwV/cHo1/FNCCiggAIKKKCAAgoooIACCiiggAIKKOAhBfw2GqQDXoyu/y4uxfdxOTrAD/FjXImf4uf4JX6NzvNb/B4CCiiggAIKKKCAAgoooIACCiiggAIKeEgB+4Gr8Uf8GV3fAf46wtUjXIu/45/oBeh5GuDf6Pr+oWP3F1BAAQUUUEABBRRQQAEFFFBAAQUUUMBDDsj1EXAk4EjAkYAjAUcCjgQcCTgScCTgSMCRgCMBRwKOBBwJOBJwJOBIwJGAIwFHAo4EHAk4EnAk4EjAkYAjAUcCjgQcCTgScCTgSMCRgCMBRwKOBBwJOBJwJOBIwJGAIwFHAo4EHAk4EnAk4EjAkYAjAUcCjgQcCTgScCTgSMCRgCMBRwKOBBwJOBJwJOBIwJGAIwFHAo4EHAk4EnAk4EjAkYAjAUcCjgQcCTgScCTgSMCRgCMBRwKOBBwJOBJwJOBIwJGAIwFHAo4EHP0HiuONhnL1EzcAAAAldEVYdGRhdGU6Y3JlYXRlADIwMjEtMDctMjJUMTU6MDg6MDMrMDA6MDCgCTTRAAAAJXRFWHRkYXRlOm1vZGlmeQAyMDIxLTA3LTIyVDE1OjA4OjAzKzAwOjAw0VSMbQAAAABJRU5ErkJggg=="

  "Decode Images" >> 
  {
    ImageUDT.deserialize(SparkPrimitive.base64Decode(IMAGE))
    ok
  }


  "load and display images" >> 
  {
    // skipped("Broken until 2.0")

    val project = MutableProject("Load Images Test")

    project.script("""
      |from PIL import Image
      |
      |ds = vizierdb.new_dataset()
      |
      |ds.insert_column("url", "string")
      |ds.insert_column("image", "image/png")
      |
      |url = "test_data/test_image.png"
      |img = Image.open(url)
      |
      |ds.insert_row([url, img])
      |ds.save("image_data", use_deltas = False)
      |ds.show()
      |
      |show(img)
    """.stripMargin)
    project.waitUntilReadyAndThrowOnError

    val outputs = project.lastOutput

    outputs must contain { (x:Message) => x.mimeType.equals(MIME.DATASET_VIEW) }
    outputs must contain { (x:Message) => x.mimeType.equals(MIME.PNG) }


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
    imageValue must not beNull
  }
}
