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