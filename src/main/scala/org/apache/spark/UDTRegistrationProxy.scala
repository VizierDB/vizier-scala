package org.apache.spark

import org.apache.spark.sql.types.{DataType, ImageUDT}
import org.apache.spark.sql.types.UDTRegistration
import java.awt.image.BufferedImage

object UDTRegistrationProxy {

  def getUDT(userClassName:String) : Class[_] = {
    if(UDTRegistration.exists(userClassName)){
      UDTRegistration.getUDTFor(userClassName).get
    }
    else throw new Exception(s"unknown UDT: $userClassName")

  }

  def register(userClass: String, udtClass: String) = {
    UDTRegistration.register(userClass, udtClass)
  }
  
}
