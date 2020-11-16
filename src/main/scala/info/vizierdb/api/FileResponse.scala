package info.vizierdb.api

import java.io._
import org.mimirdb.api.Response
import javax.servlet.http.HttpServletResponse
import info.vizierdb.util.Streams

case class FileResponse(file: File, name: String, mimeType: String) extends Response
{
  def write(output: HttpServletResponse)
  {
    output.setHeader("Content-Disposition", "attachment; filename=\""+name+"\"")
    output.setContentType(mimeType)
    Streams.cat(new FileInputStream(file), output.getOutputStream())
  }
}