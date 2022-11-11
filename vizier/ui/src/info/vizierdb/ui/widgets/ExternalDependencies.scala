package info.vizierdb.ui.widgets

import scala.collection.mutable
import org.scalajs.dom
import scalatags.JsDom.all._

object ExternalDependencies
{
  val loaded = mutable.Set[String]()

  def loadJs(url: String)(andThen: () => Unit ): Unit =
  {
    if(loaded contains url){ return }
    println(s"Load: $url")
    val tag = 
      script(
        `type` := "text/javascript",
        src := url,
      ).render
    tag.async = false
    dom.window.document.body
              .appendChild(tag)
    tag.onload = { _:dom.Event => 
      println(s"Loaded: $url")
      andThen()
    }              
    loaded.add("js:"+url)
  }
  def loadJs(urls: Seq[String])(andThen: () => Unit ): Unit =
  {
    println(s"Loading: ${urls.mkString(", ")}")
    if(urls.isEmpty){ andThen() }
    else { 
      loadJs(urls.head) { () => loadJs(urls.tail)(andThen) }
    }
  }
  def loadCss(url: String): Unit =
  {
    if(loaded contains url){ return }
    println(s"Load: $url")
    dom.window.document.head
              .appendChild(
                link(
                  rel := "stylesheet",
                  href := url
                ).render
              )
    println(s"Loaded: $url")   
    loaded.add("css:"+url)
  }
}