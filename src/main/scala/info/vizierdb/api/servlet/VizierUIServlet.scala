package info.vizierdb.api.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.Streams
import java.io.File
import java.net.URLConnection

object VizierUIServlet
  extends HttpServlet
  with LazyLogging
{
  lazy val CLASS_LOADER = getClass().getClassLoader()

  override def doGet(req: HttpServletRequest, output: HttpServletResponse) = 
  {
    try {
      var components = req.getPathInfo.split("/")
      // Strip the leading /
      if(components.headOption.equals(Some(""))){
        components = components.tail
      }
      // Ensure that we have at least one element
      if(components.isEmpty) {
        components = Array("")
      }
      // Redirect meta-URLs to the standard react index page.
      if(components(0).equals("projects")) {
        components = Array("")
      }
      // Redirect empty paths to the relevant index
      if(components.last.equals("")){
        components.update(components.size - 1, "index.html")
      } 
      // Strip out directory cheats
      components = components.filterNot { _.startsWith(".") }
      // Static files are stored in resources/ui
      components = "ui" +: components

      val resourcePath = components.mkString("/")

      logger.debug(s"STATIC GET: $resourcePath")

      val data = getClass()
                    .getClassLoader()
                    .getResourceAsStream(components.mkString("/"))
      if(data == null){
        output.setStatus(HttpServletResponse.SC_NOT_FOUND)
        output.getOutputStream().println("NOT FOUND")
      } else {
        val content = Streams.readAll(data)
        val f = new File(resourcePath)
        val mime = URLConnection.guessContentTypeFromName(f.getName())
        output.setContentType(mime)
        output.setContentLength(content.length)
        output.getOutputStream().write(content)
      }
    } catch {
      case e: Throwable => 
        e.printStackTrace()
    }
  }
}
