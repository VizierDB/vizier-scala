package org.apache.spark.sql.types

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, ByteArrayInputStream, ObjectOutputStream}
import javax.imageio.ImageIO

class ImageType extends UserDefinedType[BufferedImage] {

  override def userClass: Class[BufferedImage] = ???

  val sqlType = BinaryType

  override def serialize(obj: BufferedImage): Any = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream();
    ImageIO.write(obj, "png", baos);
    val bytes = baos.toByteArray();
    bytes
  }
  
  override def deserialize(datum: Any): BufferedImage = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream();
    val oos = new ObjectOutputStream(baos);
    oos.writeObject(datum);
    oos.close();
    val bais: ByteArrayInputStream = new ByteArrayInputStream(baos.toByteArray);
    ImageIO.read(bais);
  }
}

case object ImageType extends ImageType