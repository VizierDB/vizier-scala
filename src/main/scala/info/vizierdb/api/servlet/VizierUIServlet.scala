package info.vizierdb.api.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.Streams
import java.io.{ File, InputStream }
import java.net.URLConnection
import info.vizierdb.VizierAPI
import java.io.StringBufferInputStream

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
      // Respond to meta-URLs to the standard react index page.
      if(components(0).equals("projects")) {
        components = Array("")
      }
      // Respond to empty paths with the relevant index
      if(components.last.equals("")){
        components.update(components.size - 1, "index.html")
      } 

      // Strip out directory cheats
      components = components.filterNot { _.startsWith(".") }
      // Static files are stored in resources/ui
      components = "ui" +: components

      val resourcePath = components.mkString("/")

      logger.debug(s"STATIC GET: $resourcePath")
      println(resourcePath)

      val data: InputStream = 
        components match { 
          case Array("ui", "env.js") => overrideEnvJs()
          case _ => 
            getClass()
              .getClassLoader()
              .getResourceAsStream(components.mkString("/"))
        }
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

  def overrideEnvJs(): InputStream = 
    new StringBufferInputStream(
      s"""window.env = {
         |  API_URL: '${VizierAPI.urls.base}',
         |  API_BASIC_AUTH: false,
         |  APP_TITLE: 'Vizier',
         |  ANALYTICS_URL: '',
         |  ANALYTICS_SITE_ID: '',
         |  API_ADV_AUTH: true,
         |  PUBLIC_URL: '${VizierAPI.urls.ui}'
         |};
         |""".stripMargin
    )
}
