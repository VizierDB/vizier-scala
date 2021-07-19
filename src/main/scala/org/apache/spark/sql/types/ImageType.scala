package org.apache.spark.sql.types

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, ByteArrayInputStream, ObjectOutputStream, InputStream}
import javax.imageio.ImageIO
import scala.io.Source
import java.io.File
import java.util.Base64

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
    print(ImageIO.read(bais))
    ImageIO.read(bais)
  }
}

case object ImageUDT extends ImageUDT