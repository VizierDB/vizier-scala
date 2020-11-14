package info.vizierdb.api

import java.io._
import org.mimirdb.api.Response
import javax.servlet.http.HttpServletResponse

case class FileResponse(file: File, name: String, mimeType: String) extends Response
{
  def write(output: HttpServletResponse)
  {
    output.setHeader("Content-Disposition", "attachment; filename=\""+name+"\"")
    output.setContentType(mimeType)
    val out = output.getOutputStream()
    val in = new BufferedInputStream(new FileInputStream(file))
    val buffer = new Array[Byte](10*1024)
    var readSize = in.read(buffer, 0, buffer.size)
    while(readSize >= 0){
      if(readSize > 0){
        out.write(buffer, 0, readSize)
      } else { Thread.sleep(100) }
      readSize = in.read(buffer, 0, buffer.size)
    }
  }
}