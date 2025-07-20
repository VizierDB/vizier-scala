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