//| mvnDeps: [
//|           "org.slf4j:slf4j-simple:1.6.1",
//|           "io.bit3:jsass:5.11.0",
//|           ]
import mill.*
import mill.scalalib.*
import mill.scalalib.publish.*
import mill.scalajslib.*
import mill.javalib.SonatypeCentralPublishModule
import coursier.maven.{ MavenRepository }
import mill.api.{ Result, PathRef }
import io.bit3.jsass.{ Compiler => SassCompiler, Options => SassOptions, OutputStyle => SassOutputStyle }
import java.util.Calendar

/*************************************************
 *** The Vizier Backend 
 *************************************************/
object vizier extends ScalaModule with SonatypeCentralPublishModule {
  val VERSION       = "2.1.1"
  val PLAY_JS       = mvn"com.typesafe.play::play-json::2.9.2"
                           
  val MIMIR_CAVEATS = mvn"info.vizierdb::mimir-caveats::0.3.6"
                          .exclude(
                            "org.slf4j" -> "*",
                            "com.typesafe.play" -> "*",
                            "log4j" -> "*",
                          )

  def scalaVersion = "2.12.20"

  def repositories = super.repositories() ++ Seq(
    "https://maven.mimirdb.org/",
    "https://oss.sonatype.org/content/repositories/releases",
    "https://s01.oss.sonatype.org/content/repositories/releases",
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://repo.osgeo.org/repository/release/",
  )

  def mainClass = Some("info.vizierdb.Vizier")

  override def compile = Task {
    routes()
    super.compile()
  }

  def vizierVersionFile = Task {
    os.write(os.pwd / "vizier-version.txt", versionString())
    PathRef(os.pwd)
  }

  def baseResources = Task.Source(moduleDir / "resources")

  def sources = Task.Sources(
    moduleDir / "backend" / "src",
    moduleDir / "shared" / "src",
  )
  def resources = Task {
    Seq[PathRef](
      baseResources(),
      ui.resourceDir(),
      vizierVersionFile(),
    )
  }
  def versionString:T[String] = Task {
    val gitVersion:String = 
      os.proc("git", "branch")
        .call()
        .out.lines()
        .filter { _.startsWith("*") }
        .head
        .substring(2)
    val gitRevision: String =
      os.proc("git", "log", "--oneline")
        .call()
        .out.lines()
        .head
        .split(" ")(0)
    val date =
      Calendar.getInstance();

    f"$VERSION (revision $gitVersion-$gitRevision; built ${date.get(Calendar.YEAR)}%04d-${date.get(Calendar.MONTH)}%02d-${date.get(Calendar.DAY_OF_MONTH)}%02d)"
  }
  def version = Task {
    VERSION
  }

  def internalJavaVersion = Task {
    try {
      val jvm = System.getProperties().getProperty("java.version")
      println(f"Running Vizier with `${jvm}`")
      jvm.split("\\.")(0).toInt
    } catch {
      case _:NumberFormatException | _:ArrayIndexOutOfBoundsException => 
        println("Unable to retrieve java version.  Guessing 11+")
        11
    }
  }

  def forkArgs = Task {
    if(internalJavaVersion() >= 11){
      Seq(
        // Required on Java 11+ for Arrow compatibility
        // per: https://spark.apache.org/docs/latest/index.html
        "-Dio.netty.tryReflectionSetAccessible=true",

        // Required for Spark on java 11+
        // per: https://stackoverflow.com/questions/72230174/java-17-solution-for-spark-java-lang-noclassdeffounderror-could-not-initializ
        "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
      )
    } else { Seq[String]() }
  }

/*************************************************
 *** Backend Dependencies
 *************************************************/
  def mvnDeps = Seq(
    ////////////////////// Mimir ///////////////////////////
    MIMIR_CAVEATS
      .exclude(
        "org.apache.logging.log4j" -> "log4j-slf4j-impl"
      ),

    ////////////////////// Catalog Management //////////////
    mvn"org.scalikejdbc::scalikejdbc::4.0.0",
    mvn"org.scalikejdbc::scalikejdbc-syntax-support-macro::4.0.0",
    mvn"org.xerial:sqlite-jdbc:3.36.0.3",


    ////////////////////// Import/Export Support ///////////
    mvn"org.apache.commons:commons-compress:1.21",
    PLAY_JS.exclude(
               "com.fasterxml.jackson.core" -> "*",
             ),

    mvn"com.crealytics::spark-excel:0.13.3+17-b51cc0ac+20200722-1201-SNAPSHOT".exclude(
               "javax.servlet" -> "*",
             ), 

    ////////////////////// Interfacing /////////////////////
    mvn"org.rogach::scallop:3.4.0",

    ////////////////////// API Support /////////////////////
    mvn"com.typesafe.akka::akka-http:10.2.9",
    mvn"de.heikoseeberger::akka-http-play-json:1.39.2",
    mvn"ch.megard::akka-http-cors:1.1.3",
    mvn"com.typesafe.akka::akka-stream:2.6.19",
    mvn"com.typesafe.akka::akka-actor:2.6.19",
    mvn"com.typesafe.akka::akka-actor-typed:2.6.19",

    ////////////////////// Command-Specific Libraries //////
    // Json Import
    mvn"com.github.andyglow::scala-jsonschema::0.7.1",
    mvn"com.github.andyglow::scala-jsonschema-play-json::0.7.1",

    // XML Import
    mvn"com.databricks::spark-xml::0.15.0",

    // GIS
    // mvn"org.apache.sedona::sedona-sql-3.0:1.1.1-incubating",
    // mvn"org.apache.sedona::sedona-viz-3.0:1.1.1-incubating",
    // mvn"org.locationtech.jts:jts-core:1.18.2",
    // mvn"org.wololo:jts2geojson:0.14.3",
    // mvn"org.geotools:gt-main:24.0",
    // mvn"org.geotools:gt-referencing:24.0",
    // mvn"org.geotools:gt-epsg-hsql:24.0",
    mvn"org.apache.sedona:sedona-common:1.5.0",
    mvn"org.apache.sedona::sedona-spark-shaded-3.0:1.5.0",
    // mvn"org.apache.sedona::sedona-viz-3.0:1.5.0",
    mvn"org.datasyslab:geotools-wrapper:1.5.0-28.2",

    // Charts
    mvn"info.vizierdb::vega:1.0.0",

    // Scala Cell
    mvn"org.scala-lang:scala-compiler:2.12.20",

    // Python
    mvn"me.shadaj::scalapy-core:0.5.2",

    ////////////////////// Logging /////////////////////////
    mvn"com.typesafe.scala-logging::scala-logging::3.9.4",
    mvn"ch.qos.logback:logback-classic:1.2.10",
    mvn"org.apache.logging.log4j:log4j-core:2.17.1",
    mvn"org.apache.logging.log4j:log4j-1.2-api:2.17.1",
    mvn"org.apache.logging.log4j:log4j-jcl:2.17.1",
    mvn"org.slf4j:jul-to-slf4j:1.7.36",
  )

/*************************************************
 *** Backend Tests
 *************************************************/
  object test 
    extends ScalaTests 
    with TestModule.Specs2 
  {
    def scalaVersion = vizier.scalaVersion
    def forkArgs = vizier.forkArgs
  
    def sources = Task.Sources(
      moduleDir / os.up / "backend" / "test",
    )
    def resources = Task.Sources(
      moduleDir / os.up / "backend" / "test" / "resources",
    )

    def scalacOptions = Seq("-Yrangepos")
    def mvnDeps = Seq(
      mvn"org.scalikejdbc::scalikejdbc-test::3.4.2",
      mvn"org.specs2::specs2-core::4.19.2",
      mvn"org.specs2::specs2-matcher-extra::4.19.2",
      mvn"org.specs2::specs2-junit::4.19.2",
    )


  }

/*************************************************
 *** Backend Resources
 *************************************************/
  def buildRoutesScript = Task.Source { moduleDir / os.up / "scripts" / "build_routes.sc" }
  def routesFile        = Task.Source { moduleDir / "resources" / "vizier-routes.txt" }

  def routes = Task { 
    println("Recompiling routes from "+routesFile().path); 
    os.proc("amm", 
            buildRoutesScript().path.toString, 
           ).call( stdout = os.Inherit, 
                   stderr = os.Inherit,
                   cwd = moduleDir / os.up ) 
  }

  def publishVersion = VERSION
  override def pomSettings = PomSettings(
    description = "The Vizier Workflow System",
    organization = "info.vizierdb",
    url = "http://vizierdb.info",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("vizierdb", "vizier-scala"),
    developers = Seq(
      Developer("okennedy", "Oliver Kennedy", "https://odin.cse.buffalo.edu"),
      Developer("mrb24", "Michael Brachmann", "https://github.com/mrb24"),
      Developer("bglavic", "Boris Glavic", "http://www.cs.iit.edu/~dbgroup/members/bglavic.html"),
      Developer("hmueller", "Heiko Mueller", "https://cims.nyu.edu/~hmueller/"),
      Developer("scastelo", "Sonia Castelo", "https://github.com/soniacq"),
      Developer("maqazi", "Munaf Arshad Qazi", ""),
    )
  )

///////////////////////////////////////////////////////////////////////////

/*************************************************
 *** The Vizier Frontend / User Interface
 *************************************************/
  object ui extends ScalaJSModule { 

    def scalaVersion = vizier.scalaVersion
    def scalaJSVersion = "1.16.0"

/*************************************************
 *** Frontend Dependencies
 *************************************************/
    def mvnDeps = Seq(
      mvn"org.scala-js::scalajs-dom::1.0.0",
      mvn"com.lihaoyi::scalarx::0.4.3",
      mvn"com.lihaoyi::scalatags::0.9.4",
      mvn"com.typesafe.play::play-json::2.9.2",
    )

    def sources = Task.Sources(
      moduleDir / "src",
      vizier.moduleDir / "shared" / "src"
    )

    override def scalacOptions = Task { Seq(
      "-P:scalajs:nowarnGlobalExecutionContext"
    )}
  
    override def compile = Task {
      routes()
      super.compile()
    }

/*************************************************
 *** Frontend Tests
 *************************************************/
    object test extends ScalaJSTests with TestModule.Utest {
      def testFramework = "utest.runner.Framework"
      def mvnDeps = Seq(
        mvn"com.lihaoyi::utest::0.7.10",
      )
      import mill.scalajslib.api.JsEnvConfig
      def jsEnvConfig = 
        Task { JsEnvConfig.JsDom() }

    }
    
/*************************************************
 *** Frontend Resources
 *************************************************/
    // Vendor Javascript
    //   Javascript libraries that vizier depends on are cloned into the
    //   repository and kept in vizier/ui/vendor
    def vendor = Task.Sources(
      os.walk(moduleDir / "vendor")
        .filter { f => f.ext == "js" || f.ext == "css" }*
    )

    //   We keep a record of the licensing for all vendored libraries
    def vendorLicense = Task.Source(moduleDir / "vendor" / "LICENSE.txt")

    // HTML pages
    //   Take all of the files in vizier/ui/html and put them into the 
    //   resource directory webroot
    def html = Task.Sources(
      os.walk(moduleDir / "html")*
    )

    def sass = Task.Sources(
      os.walk(moduleDir / "css")
        .filter { _.ext == "scss" }*
    )

    def compiledSass = Task {
      val compiler = new SassCompiler()
      val options = new SassOptions()
      val target = os.pwd
      options.setOutputStyle(SassOutputStyle.COMPRESSED)

      val src = sass().filter { _.path.last == "vizier.scss" }.head
      val out = target / "vizier.css"
      println(s"IGNORE THE FOLLOWING DEPRECATION WARNING: https://gitlab.com/jsass/jsass/-/issues/95")
      val output = compiler.compileFile(
                      new java.net.URI((src.path.toString).toString),
                      new java.net.URI(out.toString),
                      options
                    )
      output.getCss
    }

    // CSS files
    //   Take all of the files in vizier/ui/css and put them into the resource
    //   directory / css
    def css = Task.Sources(
      os.walk(moduleDir / "css")
        .filter { _.ext == "css" }*
    )

    // Fonts
    //   Take all of the files in vizier/ui/fonts and put them into the resource
    //   directory / fonts
    def fonts = Task.Sources(
      os.walk(moduleDir / "fonts")*
    )

    // The following rule and function actually build the resources directory
    //     
    def resourceDir = Task { 
      val target = os.pwd

      // Vizier UI binary
      os.copy.over(
        fastLinkJS().dest.path / "main.js",
        target / "ui" / "vizier.js",
        createFolders = true,
        followLinks = true,
      )
      os.copy.over(
        fastLinkJS().dest.path / "main.js.map",
        target / "ui" / "main.js.map",
        createFolders = true,
        followLinks = true,
      )

      // Vendor JS
      for(source <- vendor().map { _.path }){
        os.copy.over(
          source,
          target / "ui" / "vendor" / source.segments.toSeq.last,
          createFolders = true,
          followLinks = true,
        )
      }
      os.write(
        target / "ui" / "vendor" / "LICENSE.txt",
        os.read(vendorLicense().path) + "\n"
      )

      val assets = html().map { x => (x.path -> os.rel / x.path.last) } ++
                   css().map { x => (x.path -> os.rel / "css" / x.path.last) } ++
                   fonts().map { x => (x.path -> os.rel / "fonts" / x.path.last) }

      // Copy Assets
      for((asset, assetTarget) <- assets){
        os.copy.over(
          asset,
          target / "ui" / assetTarget,
          createFolders = true,
          followLinks = true,
        )
      }
      os.write(
        target / "ui" / "css" / "vizier.css",
        compiledSass(),
        createFolders = true
      )

      println(s"Generated UI resource dir: $target")
      PathRef(target)
    }
  }
}

