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