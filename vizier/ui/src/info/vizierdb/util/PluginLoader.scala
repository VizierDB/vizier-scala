package info.vizierdb.util

import scala.scalajs.js
import org.scalajs.dom
import info.vizierdb.ui.components.ModuleEditorDelegate
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.ui.components.ModuleEditor
import rx.Ctx
import info.vizierdb.ui.components.{ PluginCommandRegistration, PluginCommandRegistrationSjs} 

object PluginLoader {
  val loadedPlugins = scala.collection.mutable.Map[String, FrontendPlugin]()

  /*@js.annotation.JSExportTopLevel("VizierPluginLoaderRegisterPlugin")
  def registerPlugin(packageId:String, plugin: FrontendPlugin) = {
    println(s"PluginLoader.registerPlugin: ${packageId}")
    loadedPlugins.update(packageId, plugin)
  }*/

  def loadPlugins() = {
    //TODO: Load this from a server endpoint or session
    Seq("modelingplugin").map(packageId => packageId -> loadPlugin(packageId, () => {
        val loadedPlugin = js.Dynamic.global.eval(s"${packageId};").asInstanceOf[FrontendPlugin]
        //loadedPlugin.registered()
        loadedPlugins.update(packageId, loadedPlugin)
    })).map(pkgIdScriptEl => {
        val (pkgId, scriptEl) = pkgIdScriptEl
        scriptEl
    })
  }

  def loadPlugin(packageId:String, onLoad: () => Unit) = {
    //TODO: load the js bundle from the plugin jar resources
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

trait FrontendPluginReg {
    def packageId:String
    def init():Unit
    def pluginCommandEditors:Seq[PluginCommandRegistration] 
}

trait FrontendPlugin {
    def packageId:String
    def registered():Unit
    /* def getPluginCommandEditor(packageId: String, 
                              command: PackageCommand, 
                              delegate: ModuleEditorDelegate): PluginCommandRegistration */
   def pluginCommandEditorIds:Seq[String]                           
   def pluginCommandEditor(commandId:String): js.Dynamic
}


