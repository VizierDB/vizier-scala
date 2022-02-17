import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`org.scala-js::scalajs-env-jsdom-nodejs:1.0.0`
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalajslib._
import coursier.maven.{ MavenRepository }
import mill.util.Ctx
import mill.api.{ Result, PathRef }


/*************************************************
 *** The Vizier Backend 
 *************************************************/
object vizier extends ScalaModule with PublishModule {
  val VERSION       = "2.0.0-SNAPSHOT"
  val PLAY_JS       = ivy"com.typesafe.play::play-json:2.9.2"
                           
  val MIMIR_CAVEATS = ivy"org.mimirdb::mimir-caveats::0.3.5"
                          .exclude(
                            "org.slf4j" -> "*",
                            "org.mortbay.jetty" -> "*",
                            "com.typesafe.play" -> "*",
                            "log4j" -> "*",
                          )

  def scalaVersion = "2.12.15"
  def moduleDeps = Seq(vega)

  def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
    MavenRepository("https://maven.mimirdb.org/"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://repo.osgeo.org/repository/release/"),
  )}

  def mainClass = Some("info.vizierdb.Vizier")

  override def compile = T {
    routes()
    super.compile()
  }

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / "shared-src"
  )
  def resources = T.sources(
    millSourcePath / "resources",
    ui.resourceDir()
  )

/*************************************************
 *** Backend Dependencies
 *************************************************/
  def ivyDeps = Agg(
    ////////////////////// Mimir ///////////////////////////
    MIMIR_CAVEATS,

    ////////////////////// Catalog Management //////////////
    ivy"org.scalikejdbc::scalikejdbc::4.0.0",
    ivy"org.scalikejdbc::scalikejdbc-syntax-support-macro::4.0.0",
    ivy"org.xerial:sqlite-jdbc:3.36.0.3",


    ////////////////////// Import/Export Support ///////////
    ivy"org.apache.commons:commons-compress:1.20",
    PLAY_JS.exclude(
               "com.fasterxml.jackson.core" -> "*",
             ),

    ////////////////////// Interfacing /////////////////////
    ivy"org.rogach::scallop:3.4.0",

    ////////////////////// API Support /////////////////////
    ivy"javax.servlet:javax.servlet-api:3.1.0",
    ivy"org.eclipse.jetty.websocket:websocket-server:9.4.44.v20210927",

    ////////////////////// Command-Specific Libraries //////
    // Json Import
    ivy"com.github.andyglow::scala-jsonschema::0.7.1",
    ivy"com.github.andyglow::scala-jsonschema-play-json::0.7.1",

    // GIS
    ivy"org.apache.sedona::sedona-core-3.0:1.1.1-incubating",
    ivy"org.apache.sedona::sedona-sql-3.0:1.1.1-incubating",
    ivy"org.apache.sedona::sedona-viz-3.0:1.1.1-incubating",
    ivy"org.locationtech.jts:jts-core:1.18.2",
    ivy"org.wololo:jts2geojson:0.14.3",
    ivy"org.geotools:gt-main:24.0",
    ivy"org.geotools:gt-referencing:24.0",
    ivy"org.geotools:gt-epsg-hsql:24.0",

    // Scala Cell
    ivy"org.scala-lang:scala-compiler:${scalaVersion}",

    ////////////////////// Logging /////////////////////////
    ivy"com.typesafe.scala-logging::scala-logging::3.9.4",
    ivy"ch.qos.logback:logback-classic:1.2.10",
    ivy"org.apache.logging.log4j:log4j-core:2.17.1",
    ivy"org.apache.logging.log4j:log4j-1.2-api:2.17.1",
    ivy"org.apache.logging.log4j:log4j-jcl:2.17.1",
  )

/*************************************************
 *** Backend Tests
 *************************************************/
  object test 
    extends Tests 
    with TestModule.Specs2 
  {
    def scalaVersion = vizier.scalaVersion

    def scalacOptions = Seq("-Yrangepos")
    def ivyDeps = Agg(
      ivy"org.scalikejdbc::scalikejdbc-test::3.4.2",
      ivy"org.specs2::specs2-core::4.8.2",
      ivy"org.specs2::specs2-matcher-extra::4.8.2",
      ivy"org.specs2::specs2-junit::4.8.2",
    )

  }

/*************************************************
 *** Backend Resources
 *************************************************/
  def buildRoutesScript = T.sources { os.pwd / "scripts" / "build_routes.py" }
  def routesFile        = T.sources { millSourcePath / "resources" / "vizier-routes.txt" }

  def routes = T { 
    println("Recompiling routes from "+routesFile().head.path); 
    os.proc("python3", buildRoutesScript().head.path.toString).call() 
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
      vizier.millSourcePath / "shared-src"
    )
  
    override def compile = T {
      routes()
      super.compile()
    }

/*************************************************
 *** Frontend Tests
 *************************************************/
    object test extends Tests with TestModule.Utest {
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

    // CSS files
    //   Take all of the files in vizier/ui/css and put them into the resource
    //   directory / css
    def css = T.sources {
      os.walk(millSourcePath / "css")
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
      buildUIResourceDir(
        uiBinary = fastOpt().path,
        vendor = ( vendor().map { _.path }, os.read(vendorLicense().path) ),
        assets = html().map { x => (x.path -> os.rel / x.path.last) } ++
                 css().map { x => (x.path -> os.rel / "css" / x.path.last) } ++
                 fonts().map { x => (x.path -> os.rel / "fonts" / x.path.last) }

      )
    }
  
    def buildUIResourceDir(
      uiBinary: os.Path,
      vendor: (Seq[os.Path], String),
      assets: Seq[(os.Path, os.RelPath)]
    )(implicit ctx: Ctx): Result[os.Path] =
    {
      val target = ctx.dest

      // Vizier UI binary
      os.copy.over(
        uiBinary,
        target / "ui" / "vizier.js",
        createFolders = true
      )
      os.copy.over(
        uiBinary / os.up / (uiBinary.last+".map"),
        target / "ui" / (uiBinary.last+".map"),
        createFolders = true
      )

      // Vendor JS
      for(source <- vendor._1){
        os.copy.over(
          source,
          target / "ui" / "vendor" / source.segments.toSeq.last,
          createFolders = true
        )
      }
      os.write(
        target / "ui" / "vendor" / "LICENSE.txt",
        vendor._2 + "\n"
      )

      // Assets
      for((asset, assetTarget) <- assets){
        os.copy.over(
          asset,
          target / "ui" / assetTarget,
          createFolders = true
        )
      }

      println(s"Generated UI resource dir: $target")
      return target
    }
  }

  object vega extends ScalaModule with PublishModule {
    def publishVersion = vizier.VERSION
    def scalaVersion = vizier.scalaVersion

    def sources = T.sources(
      vizier.millSourcePath / "vega-src"
    )

    override def pomSettings = PomSettings(
      description = "Vizier Vega/Vega-Lite Support",
      organization = "info.vizierdb",
      url = "http://vizierdb.info",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("vizierdb", "vizier-scala"),
      developers = Seq(
        Developer("okennedy", "Oliver Kennedy", "https://odin.cse.buffalo.edu"),
      )
    )
    def ivyDeps = Agg(
      vizier.PLAY_JS
    )
  }
}

