package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import org.mimirdb.api.Request
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.api.response.RawJsonResponse

object ListTasks
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      RawJsonResponse(
        JsArray(
          Scheduler.running
                   .map { workflow => workflow.summarize }
        )
      )
    }
  } 
}