package info.vizierdb.util

import java.net.URL

object HttpUtils {
  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String,
          connectTimeout: Int = 8000,
          readTimeout: Int = 8000,
          requestMethod: String = "GET") =
  {
      import java.net.{URL, HttpURLConnection}
      val connection = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(connectTimeout)
      connection.setReadTimeout(readTimeout)
      connection.setRequestMethod(requestMethod)
      val inputStream = connection.getInputStream
      val content = scala.io.Source.fromInputStream(inputStream).mkString
      if (inputStream != null) inputStream.close
      content
  }
}