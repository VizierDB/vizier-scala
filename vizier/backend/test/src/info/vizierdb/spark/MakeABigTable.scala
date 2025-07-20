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
package info.vizierdb.spark

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.Vizier
import scala.concurrent.Future

class MakeABigTable 
	extends Specification 
	with BeforeAll
{
	def beforeAll = SharedTestResources.init()
	implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

	"Make a big table" >> 
	{

		val project = MutableProject("Project With A Big Table")

		project.script("""vizierdb.outputDataset("big",
			               |	info.vizierdb.spark.RangeConstructor(0, 10000)
			               |)
									   """.stripMargin, language = "scala")

		project.sql("""
	                |SELECT id,
	                |	      CASE 
	                |         WHEN mod(id, 3) = 0 AND mod(id, 5) = 0 THEN 'fizzbuzz'
	                |         WHEN mod(id, 3) = 0 THEN 'fizz'
	                |         WHEN mod(id, 5) = 0 THEN 'buzz'
	                |         ELSE id
		              |       END AS fizzbuzz,
		              |       caveatif(cast(id / 2 as int), mod(id, 2) = 1, "rounding error") AS halfid
	                |FROM big
	                """.stripMargin -> "big")

		project.dataframe("big").show()

		// project.sql(
		// 	"SELECT * FROM "
		// )
		ok
	}
}