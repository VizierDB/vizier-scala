import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`org.scala-js::scalajs-env-jsdom-nodejs:1.0.0`
import $ivy.`org.slf4j:slf4j-simple:1.6.1`
import $ivy.`io.bit3:jsass:5.10.4`
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalajslib._
import coursier.maven.{ MavenRepository }
import mill.api.{ Result, PathRef }
import io.bit3.jsass.{ Compiler => SassCompiler, Options => SassOptions, OutputStyle => SassOutputStyle }
import java.util.Calendar

/*************************************************
 *** The Vizier Backend 
 *************************************************/
object vizier extends ScalaModule with PublishModule {
  val VERSION       = "2.1.0-SNAPSHOT"
  val PLAY_JS       = ivy"com.typesafe.play::play-json::2.9.2"
                           
  val MIMIR_CAVEATS = ivy"info.vizierdb::mimir-caveats::0.3.6"
                          .exclude(
                            "org.slf4j" -> "*",
                            "com.typesafe.play" -> "*",
                            "log4j" -> "*",
                          )

  def scalaVersion = "2.12.15"

  def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
    MavenRepository("https://maven.mimirdb.org/"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
    MavenRepository("https://s01.oss.sonatype.org/content/repositories/releases"),
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://repo.osgeo.org/repository/release/"),
  )}

  def mainClass = Some("info.vizierdb.Vizier")

  override def compile = T {
    routes()
    super.compile()
  }

  def sources = T.sources {
    super.sources() ++ Seq[PathRef](
      PathRef(millSourcePath / "backend" / "src"),
      PathRef(millSourcePath / "shared" / "src"),
    )
  }
  def resources = T.sources {
    os.write(T.dest / "vizier-version.txt", versionString())
    super.resources() ++ Seq[PathRef](
      PathRef(millSourcePath / "resources"),
      PathRef(ui.resourceDir()),
      PathRef(T.dest)
    )
  }
  def versionString:T[String] = T {
    val gitVersion:String = 
      os.proc("git", "branch")
        .call()
        .out.lines()
        .filter { _ startsWith "*" }
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

  def internalJavaVersion = T {
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

  def forkArgs = T {
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
  def ivyDeps = Agg(
    ////////////////////// Mimir ///////////////////////////
    MIMIR_CAVEATS
      .exclude(
        "org.apache.logging.log4j" -> "log4j-slf4j-impl"
      ),

    ////////////////////// Catalog Management //////////////
    ivy"org.scalikejdbc::scalikejdbc::4.0.0",
    ivy"org.scalikejdbc::scalikejdbc-syntax-support-macro::4.0.0",
    ivy"org.xerial:sqlite-jdbc:3.36.0.3",


    ////////////////////// Import/Export Support ///////////
    ivy"org.apache.commons:commons-compress:1.21",
    PLAY_JS.exclude(
               "com.fasterxml.jackson.core" -> "*",
             ),

    ivy"com.crealytics::spark-excel:0.13.3+17-b51cc0ac+20200722-1201-SNAPSHOT".exclude(
               "javax.servlet" -> "*",
             ), 

    ////////////////////// Interfacing /////////////////////
    ivy"org.rogach::scallop:3.4.0",

    ////////////////////// API Support /////////////////////
    ivy"com.typesafe.akka::akka-http:10.2.9",
    ivy"de.heikoseeberger::akka-http-play-json:1.39.2",
    ivy"ch.megard::akka-http-cors:1.1.3",
    ivy"com.typesafe.akka::akka-stream:2.6.19",
    ivy"com.typesafe.akka::akka-actor:2.6.19",
    ivy"com.typesafe.akka::akka-actor-typed:2.6.19",

    ////////////////////// Command-Specific Libraries //////
    // Json Import
    ivy"com.github.andyglow::scala-jsonschema::0.7.1",
    ivy"com.github.andyglow::scala-jsonschema-play-json::0.7.1",

    // XML Import
    ivy"com.databricks::spark-xml::0.15.0",

    // GIS
    // ivy"org.apache.sedona::sedona-sql-3.0:1.1.1-incubating",
    // ivy"org.apache.sedona::sedona-viz-3.0:1.1.1-incubating",
    // ivy"org.locationtech.jts:jts-core:1.18.2",
    // ivy"org.wololo:jts2geojson:0.14.3",
    // ivy"org.geotools:gt-main:24.0",
    // ivy"org.geotools:gt-referencing:24.0",
    // ivy"org.geotools:gt-epsg-hsql:24.0",
    ivy"org.apache.sedona:sedona-common:1.5.0",
    ivy"org.apache.sedona::sedona-spark-shaded-3.0:1.5.0",
    // ivy"org.apache.sedona::sedona-viz-3.0:1.5.0",
    ivy"org.datasyslab:geotools-wrapper:1.5.0-28.2",

    // Charts
    ivy"info.vizierdb::vega:1.0.0",

    // Scala Cell
    ivy"org.scala-lang:scala-compiler:${scalaVersion}",

    // Python
    ivy"me.shadaj::scalapy-core:0.5.2",

    ////////////////////// Logging /////////////////////////
    ivy"com.typesafe.scala-logging::scala-logging::3.9.4",
    ivy"ch.qos.logback:logback-classic:1.2.10",
    ivy"org.apache.logging.log4j:log4j-core:2.17.1",
    ivy"org.apache.logging.log4j:log4j-1.2-api:2.17.1",
    ivy"org.apache.logging.log4j:log4j-jcl:2.17.1",
    ivy"org.slf4j:jul-to-slf4j:1.7.36",
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
  
    def sources = T.sources(
      millSourcePath / os.up / "backend" / "test",
    )
    def resources = T.sources(
      millSourcePath / os.up / "backend" / "test" / "resources",

    )

    def scalacOptions = Seq("-Yrangepos")
    def ivyDeps = Agg(
      ivy"org.scalikejdbc::scalikejdbc-test::3.4.2",
      ivy"org.specs2::specs2-core::4.19.2",
      ivy"org.specs2::specs2-matcher-extra::4.19.2",
      ivy"org.specs2::specs2-junit::4.19.2",
    )


  }

/*************************************************
 *** Backend Resources
 *************************************************/
  def buildRoutesScript = T.sources { os.pwd / "scripts" / "build_routes.sc" }
  def routesFile        = T.sources { millSourcePath / "resources" / "vizier-routes.txt" }

  def routes = T { 
    println("Recompiling routes from "+routesFile().head.path); 
    os.proc("amm", buildRoutesScript().head.path.toString)
                                          .call( stdout = os.Inherit, stderr = os.Inherit) 
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
    def scalaJSVersion = "1.7.1"

/*************************************************
 *** Frontend Dependencies
 *************************************************/
    def ivyDeps = Agg(
      ivy"org.scala-js::scalajs-dom::1.0.0",
      ivy"com.lihaoyi::scalarx::0.4.3",
      ivy"com.lihaoyi::scalatags::0.9.4",
      ivy"com.typesafe.play::play-json::2.9.2",
    )

    def sources = T.sources(
      millSourcePath / "src",
      vizier.millSourcePath / "shared" / "src"
    )
  
    override def compile = T {
      routes()
      super.compile()
    }

/*************************************************
 *** Frontend Tests
 *************************************************/
    object test extends ScalaJSTests with TestModule.Utest {
      def testFramework = "utest.runner.Framework"
      def ivyDeps = Agg(
        ivy"com.lihaoyi::utest::0.7.10",
      )
      import mill.scalajslib.api.JsEnvConfig
      def jsEnvConfig = 
        T { JsEnvConfig.JsDom() }

    }
    
/*************************************************
 *** Frontend Resources
 *************************************************/
    // Vendor Javascript
    //   Javascript libraries that vizier depends on are cloned into the
    //   repository and kept in vizier/ui/vendor
    def vendor = T.sources(
      os.walk(millSourcePath / "vendor")
        .filter { f => f.ext == "js" || f.ext == "css" }
        .map { PathRef(_) }
    )

    //   We keep a record of the licensing for all vendored libraries
    def vendorLicense = T.source(millSourcePath / "vendor" / "LICENSE.txt")

    // HTML pages
    //   Take all of the files in vizier/ui/html and put them into the 
    //   resource directory webroot
    def html = T.sources(
      os.walk(millSourcePath / "html")
        .map { PathRef(_) }
    )

    def sass = T.sources {
      os.walk(millSourcePath / "css")
        .filter { _.ext == "scss" }
        .map { PathRef(_) }
    }

    def compiledSass = T {
      val compiler = new SassCompiler()
      val options = new SassOptions()
      val target = T.dest
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
    def css = T.sources {
      os.walk(millSourcePath / "css")
        .filter { _.ext == "css" }
        .map { PathRef(_) }
    }

    // Fonts
    //   Take all of the files in vizier/ui/fonts and put them into the resource
    //   directory / fonts
    def fonts = T.sources {
      os.walk(millSourcePath / "fonts")
        .map { PathRef(_) }
    }

    // The following rule and function actually build the resources directory
    //     
    def resourceDir = T { 
      val target = T.dest

      // Vizier UI binary
      os.copy.over(
        fastLinkJS().dest.path / "main.js",
        target / "ui" / "vizier.js",
        createFolders = true
      )
      os.copy.over(
        fastLinkJS().dest.path / "main.js.map",
        target / "ui" / "main.js.map",
        createFolders = true
      )

      // Vendor JS
      for(source <- vendor().map { _.path }){
        os.copy.over(
          source,
          target / "ui" / "vendor" / source.segments.toSeq.last,
          createFolders = true
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
          createFolders = true
        )
      }
      os.write(
        target / "ui" / "css" / "vizier.css",
        compiledSass(),
        createFolders = true
      )

      println(s"Generated UI resource dir: $target")
      target
    }
  }
}

