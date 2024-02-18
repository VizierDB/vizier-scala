package info.vizierdb.profiler

import scalikejdbc._
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.api._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.commands.data.{ UnloadDataset, LoadDataset }
import info.vizierdb.Vizier


class ProfileCommandSpec extends Specification with BeforeAll
{
  def beforeAll = SharedTestResources.init

  sequential

  "testing profiler" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties

    properties.get("is_profiled") must beSome.which(_ == JsBoolean(true))
  }

   "testing no profiler" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("false"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties

    properties.get("is_profiled") must beNone
  }

  "testing one profile one non profile" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    val project2 = MutableProject("test_profiler1")
    project2.load(
      file = "test_data/output_with_nulls.csv",
      name = "test2", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("false"),
    )
    GetArtifact(
      projectId = project2.projectId,
      artifactId = project2.artifact("test2").id,
      profile = Some("true"),
    )

    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties
    val art2 = project2.artifact("test2")
    val properties2 = art2.datasetDescriptor.properties

    properties.get("is_profiled") must beNone
    properties2.get("is_profiled") must beSome.which(_ == JsBoolean(true))
  
  }

  "testing correct profiler information" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties
    val columnsProperty = properties.get("columns")

    columnsProperty match {
      case Some(jsValue: JsValue) =>
        // Parse the JSON string
        val json = Json.parse(jsValue.toString())

        // Validate and extract columns array
        (json \ "columns").validate[JsArray] match {
          case JsSuccess(columnsArray, _) =>
            columnsArray.value.foreach { column =>
              // Extract and test distinctValueCount
              val distinctValueCount = (column \ "distinctValueCount").as[Int]
              distinctValueCount must beEqualTo(0)

              // Extract and test nullCount
              val nullCount = (column \ "nullCount").as[Int]
              nullCount must beEqualTo(25)

              println(s"Column Test Passed: distinctValueCount: $distinctValueCount, nullCount: $nullCount")
            }

          case JsError(errors) =>
            failure(s"Error parsing columns JSON: $errors")
        }

      case _ => 
        failure("Columns property not found or not a valid JSON object")
    }

    ok
  }

  "testing correct profiler distinct values" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/NYC_18_Dec_2018.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties
    val columnsProperty = properties.get("columns")


    columnsProperty match {
      case Some(jsValue: JsValue) =>
        // Parse the JSON string
        val json = Json.parse(jsValue.toString())
        // Validate and extract columns array
        (json \ "columns").validate[JsArray] match {
          case JsSuccess(columnsArray, _) =>
            columnsArray.value.foreach { column =>
              // Extract and test distinctValueCount
              val distinctValueCount = (column \ "distinctValueCount").as[Int]
              val returnedValues = (column \ "values")
              // Testing if values get returned or not
              if(distinctValueCount > 20){
                (returnedValues) match {
                  case JsDefined(_) => failure("When distinct Value is over 20 then no values should be returned")
                  case JsUndefined() => success 
                }
              }else {
                (returnedValues) match {
                  case JsDefined(_) => success
                  case JsUndefined() => failure("Values should be returned when distinct Value is 20 or under")
                }
              }
            }

          case JsError(errors) =>
            failure(s"Error parsing columns JSON: $errors")
        }

      case _ => 
        failure("Columns property not found or not a valid JSON object")
    }

    ok
  }


  "testing correct profiler min max mean" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/test_minmaxmean_columns.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties
    val columnsProperty = properties.get("columns")


    columnsProperty match {
      case Some(jsValue: JsValue) =>
        // Parse the JSON string
        val json = Json.parse(jsValue.toString())
        // Validate and extract columns array
        (json \ "columns").validate[JsArray] match {
          case JsSuccess(columnsArray, _) =>
            columnsArray.value.foreach { column =>
              val columnInfo = (column \ "column")
              val columnType = (columnInfo \ "type")
              val minValue = (column \ "min") 
              val maxValue = (column \ "max")
              val meanValue = (column \ "mean")
              columnType match {
                case JsDefined(JsString(valueType)) if valueType == "integer" || valueType == "double" =>
                  minValue match {
                    case JsDefined(_) => success
                    case JsUndefined() => failure("integer is supposed to have a min value")
                  }
                  maxValue match {
                    case JsDefined(_) => success
                    case JsUndefined() => failure("integer is supposed to have a max value")
                  }
                  meanValue match {
                    case JsDefined(_) => success
                    case JsUndefined() => failure("integer is supposed to have a mean value")
                  }
                case JsDefined(JsString(_)) => 
                  minValue match {
                    case JsDefined(_) => failure("type is not supposed to have a min value")
                    case JsUndefined() => success
                  }
                  maxValue match {
                    case JsDefined(_) => failure("type is not supposed to have a max value")
                    case JsUndefined() => success
                  }
                  meanValue match {
                    case JsDefined(_) => failure("type is not supposed to have a mean value")
                    case JsUndefined() => success
                  }
                case JsUndefined() => failure("should contain a type")
              }
            }
          case JsError(errors) =>
            failure(s"Error parsing columns JSON: $errors")
        }

      case _ => 
        failure("Columns property not found or not a valid JSON object")
    }

    ok
  }
  



}
