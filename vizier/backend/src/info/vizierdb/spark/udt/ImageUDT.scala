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