package info.vizierdb.util

class UnsupportedFeature(val message: String) extends Exception(message)

object FeatureSupported
{
  val javaVersion = System.getProperty("java.version", "0.0.0")

  val (majorVersion,
       minorVersion,
       revisionVersion) = 
  {
    // println(s"########## Java Version: $javaVersion")
    val split = javaVersion.split("[^0-9A-Za-z]+")
    // println(s"########## Split: $javaVersion")
    (split(0).toInt, split(1).toInt, split(2).toInt)
  }

  def requiresJavaVersion(feature: String, majorRequired: Int): Unit =
    requiresJavaVersion(feature, majorRequired, 0, 0)
  def requiresJavaVersion(feature: String, majorRequired: Int, minorRequired: Int): Unit =
    requiresJavaVersion(feature, majorRequired, minorRequired, 0)
  def requiresJavaVersion(feature: String, majorRequired: Int, minorRequired: Int, revisionRequired: Int): Unit =
  {
    if(  (majorRequired > majorVersion)
      || (majorRequired == majorVersion && minorRequired > minorVersion)
      || (majorRequired == majorVersion && minorRequired == minorVersion && revisionRequired > revisionVersion)
    ){
      throw new UnsupportedFeature(s"$feature requires java $majorRequired.$minorRequired.$revisionRequired; You have $javaVersion")
    }
  }

  def brokenByJavaVersion(feature: String, majorBroken: Int): Unit =
    brokenByJavaVersion(feature, majorBroken, 0, 0)
  def brokenByJavaVersion(feature: String, majorBroken: Int, minorBroken: Int): Unit =
    brokenByJavaVersion(feature, majorBroken, minorBroken, 0)
  def brokenByJavaVersion(feature: String, majorBroken: Int, minorBroken: Int, revisionBroken: Int): Unit =
  {
    if(  (majorBroken < majorVersion)
      || (majorBroken == majorVersion && minorBroken < minorVersion)
      || (majorBroken == majorVersion && minorBroken == minorVersion && revisionBroken < revisionVersion)
    ){
      throw new UnsupportedFeature(s"$feature was broken by java $majorBroken.$minorBroken.$revisionBroken; You have $javaVersion")
    }
  }

}