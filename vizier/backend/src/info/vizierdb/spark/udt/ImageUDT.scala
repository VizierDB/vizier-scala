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
package info.vizierdb.spark.udt


import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, ByteArrayInputStream, ObjectOutputStream, InputStream}
import javax.imageio.ImageIO
import org.apache.spark.sql.types.{ UserDefinedType, BinaryType }

class ImageUDT extends UserDefinedType[BufferedImage] {

  override def userClass: Class[BufferedImage] = classOf[BufferedImage]

  val sqlType = BinaryType

  override def serialize(obj: BufferedImage): Any = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
    ImageIO.write(obj, "png", baos)
    baos.toByteArray()
  }
  
  override def deserialize(datum: Any): BufferedImage = {
    val bais: InputStream = new ByteArrayInputStream(datum.asInstanceOf[Array[Byte]])
    ImageIO.read(bais)
  }
}

case object ImageUDT extends ImageUDT