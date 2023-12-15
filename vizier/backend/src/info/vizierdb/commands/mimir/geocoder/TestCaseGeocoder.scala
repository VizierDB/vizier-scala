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
package info.vizierdb.commands.mimir.geocoder

import scala.util.Random
import play.api.libs.json._

class TestCaseGeocoder(name: String) 
  extends Geocoder(name, s"No-op $name Geocoder")
{
  def apply(house: String, street: String, city: String, state: String): Seq[Double] =
  {
    val rnd = new Random(Seq(house, street, city, state).mkString("; ").hashCode)

    val target = rnd.nextDouble

    if(target < 0.7){
      Seq(
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180
      )
    } else if(target < 0.9){
      Seq(
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180,
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180
      )
    } else { Seq() }

  }
}

object TestCaseGeocoder extends TestCaseGeocoder("TEST")
{
  def apply(name: String) = new TestCaseGeocoder(name)
}