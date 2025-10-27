package info.vizierdb.util

import scala.scalajs.js
import org.scalajs.dom
import info.vizierdb.ui.components.ModuleEditorDelegate
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.ui.components.ModuleEditor
import rx.Ctx
import info.vizierdb.ui.components.PluginCommandRegistration

object PluginLoader {
  val loadedPlugins = scala.collection.mutable.Map[String, FrontendPlugin]()

  def loadPlugins() = {
    //TODO: Load this from a server endpoint or session
    Seq("modelingplugin").map(packageId => packageId -> loadPlugin(packageId, () => {
        val loadedPlugin = js.Dynamic.global.eval(s"${packageId}").asInstanceOf[FrontendPlugin]
        //loadedPlugin.init()
        loadedPlugins.update(packageId, loadedPlugin)
    })).map(pkgIdScriptEl => {
        val (pkgId, scriptEl) = pkgIdScriptEl
        scriptEl
    })
  }

  def loadPlugin(packageId:String, onLoad: () => Unit) = {
    loadJs(s"/vendor/${packageId}.js", onLoad)
  }
  
  /** Dynamically load a .js file into the global scope */
  private def loadJs(url: String, onLoad: () => Unit): Unit = {
    val scriptElem = dom.document.createElement("script").asInstanceOf[dom.raw.HTMLScriptElement]
    scriptElem.`type` = "text/javascript"
    scriptElem.src = url
    // (optional) Set async, defer, etc. as needed
    scriptElem.async = false
    scriptElem.onload = (_: dom.Event) => onLoad()
    dom.document.head.appendChild(scriptElem)
    scriptElem
  }
}

trait FrontendPlugin {
    def packageId:String
    def init():Unit
    def getPluginCommandEditor(packageId: String, 
                              command: PackageCommand, 
                              delegate: ModuleEditorDelegate): Option[PluginCommandRegistration]
}

// Usage example:
// ScriptLoader.loadPlugin("modelingplugin")
