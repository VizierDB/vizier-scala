package info.vizierdb.util

import scala.scalajs.js
import org.scalajs.dom
import info.vizierdb.ui.components.ModuleEditorDelegate
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.ui.components.ModuleEditor
import rx.Ctx
import scala.scalajs.js.annotation.JSExportTopLevel

import info.vizierdb.ui.components.Parameter
import info.vizierdb.serialized
import scala.util.Success
import scala.util.Failure

//import scala.scalajs.js.annotation.JSImport
trait PluginModule extends js.Object {
  def registerPlugin():Unit;
}

object PluginLoader {
  @JSExportTopLevel("frontendPluginRegistry")
  val loadedPlugins: js.Array[FrontendPlugin] = new js.Array[FrontendPlugin]()

  def loadPlugins() = {
    //old way
    /*Seq("modelingplugin").map(packageId => packageId -> loadPlugin(packageId, () => {
        // For non-ES config
        //val loadedPlugin = js.Dynamic.global.eval(s"${packageId};").asInstanceOf[FrontendPlugin]
        // or
        js.Dynamic.global.eval(s"register${packageId}();")
        
        // For ES Module config
        runESRegister(packageId, () => { println(s"runESRegister loaded for: ${packageId}") })
        //or
        // implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
        // js.`import`[PluginModule](s"http://localhost:5050/vendor/${packageId}.js").toFuture.onComplete {
        //   case Success(module) => {
        //     module.registermodelingplugin()
        //     //runESRegister(packageId, () => { println(s"runESRegister loaded for: ${packageId}") })
        //   }
        //   case Failure(e) => println(s"error ${e}")
        // }
        })).map(pkgIdScriptEl => {
        val (pkgId, scriptEl) = pkgIdScriptEl
        scriptEl
    })*/
    
    //my hack way
    //TODO: Load this Seq from a server endpoint or session
    Seq("modelingplugin").map( packageId => { 
      //fetchAndLoadJs(s"/vendor/${packageId}.js")
      /*runESRegister(packageId, (evt:dom.Event) => {
        println(s"package module onload: ${packageId}")
      })*/
      fetchAndLoadJsWebpackBundle(packageId)
      println(s"package module load called: ${packageId}")
      //loadedPlugins.update(packageId, loadedPlugin)
    })
  }

  def loadPlugin(packageId:String, onLoad: () => Unit) = {
    //TODO: load the js bundle from the plugin jar resources
    loadJs(s"/vendor/${packageId}.js", onLoad)
  }

  private def fetchAndLoadJs(url:String) = {
    import dom.fetch
    import js.Thenable.Implicits._
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

    val responseText = for {
      response <- fetch(url)
      text <- response.text()
    } yield text

    responseText.onComplete {
      case Success(text) =>
        js.eval(text).asInstanceOf[PluginModule].registerPlugin()
      case Failure(e) =>
        println(s"Error: ${e.getMessage}")
    }
  }

   private def fetchAndLoadJsWebpackBundle(packageId:String) = {
    import dom.fetch
    import js.Thenable.Implicits._
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

    val responseText = for {
      response <- fetch(s"/vendor/${packageId}.js")
      text <- response.text()
    } yield text

    responseText.onComplete {
      case Success(text) => {
        js.eval(text)
        println(s"fetchAndLoadJsWebpackBundle: ${text.length()}")
        js.eval(s"window.${packageId}.registerPlugin();")//dom.window.asInstanceOf[js.Dynamic](packageId).registerPlugin()
      }
      case Failure(e) =>
        println(s"Error: ${e.getMessage}")
    }
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

  private def runESRegister(moduleImportName: String, onLoad: (dom.Event) => Unit): Unit = {
    val scriptElem = dom.document.createElement("script").asInstanceOf[dom.raw.HTMLScriptElement]
    scriptElem.`type` = "module"//"text/javascript"
    //scriptElem.src = s"""data:text/javascript,import * as ${moduleImportName} from 'http://localhost:5050/vendor/${moduleImportName}.js';  /*${moduleImportName}.register${moduleImportName}();*/ console.log("register plugin done....")"""
    scriptElem.src = s"""data:text/javascript,
    //import * as ${moduleImportName} from 'http://localhost:5050/vendor/${moduleImportName}.js';   
    console.log("registering plugin: ${moduleImportName}....");
    async function doimport() {
      const module = await import('http://localhost:5050/vendor/${moduleImportName}.js');
      module.registerPlugin();
    };
    doimport();
    console.log("register plugin done....");"""
    // (optional) Set async, defer, etc. as needed
    scriptElem.async = false
    scriptElem.onload = onLoad
    dom.document.head.appendChild(scriptElem)
    scriptElem
  }
}

@js.native
trait FrontendPlugin extends js.Object {
  def packageId:String
  def registered():Unit
  def pluginCommandEditorIds:Seq[String]                           
  def pluginCommandEditor(commandId:String): js.Dynamic
}

@js.native
trait PluginCommandRegistration extends js.Object {
  def commandId:String
  def beginDisplay(artifacts:js.Array[serialized.ArtifactSummary], state:js.Array[Parameter]):js.Array[Parameter]
  def endDisplay: js.Array[Parameter] 
  def editorFields:dom.Element 
}
