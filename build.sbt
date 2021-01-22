scalaVersion := "2.12.12"

val VIZIER_VERSION = "0.4-SNAPSHOT"
val MIMIR_VERSION = "0.4"
val CAVEATS_VERSION = "0.3.0"

// Project and subprojects
lazy val vizier = (project in file("."))
                      .dependsOn(
                        mimir, 
                        caveats
                      )
                      .settings(
                        name := "vizier",
                        version := VIZIER_VERSION,
                        organization := "info.vizierdb"
                      )
lazy val mimir = (project in file("upstream/mimir"))
                      .dependsOn(
                        caveats
                      )
                      .settings(
                        // flag this artificial dependency so we can remove
                        // it when generating the POM file
                        organization := "remove-me"
                      )
lazy val caveats = (project in file("upstream/caveats"))
                      .settings(
                        // flag this artificial dependency so we can remove
                        // it when generating the POM file
                        organization := "remove-me"
                      )

// Make the UX work in SBT
fork := true
outputStrategy in run := Some(StdoutOutput)
connectInput in run := true
cancelable in Global := true

// Produce Machine-Readable JUnit XML files for tests
testOptions in Test ++= Seq( Tests.Argument("junitxml"), Tests.Argument("console") )

// Auto-reload on edits
Global / onChangedBuildSource := ReloadOnSourceChanges

// Specs2 Requirement:
scalacOptions in Test ++= Seq("-Yrangepos")


// Support Test Resolvers
resolvers += "MimirDB" at "https://maven.mimirdb.info/"
resolvers += Resolver.typesafeRepo("releases")
resolvers += DefaultMavenRepository
resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)
resolvers += Resolver.mavenLocal

excludeDependencies ++= Seq(
  // Hadoop brings in more logging backends.  Kill it with fire.
  ExclusionRule("org.slf4j", "slf4j-log4j12"),
  // Jetty shows up in infinite places with incompatible java servlet APIs
  // ExclusionRule( organization = "org.xerial"), 
  ExclusionRule( organization = "org.mortbay.jetty"), 
)

// Custom Dependencies
libraryDependencies ++= Seq(
  // Mimir
  "org.mimirdb"                   %% "mimir-api"                        % MIMIR_VERSION,
  "org.mimirdb"                   %% "mimir-caveats"                    % CAVEATS_VERSION,

  // Catalog management (trying this out... might be good to bring into mimir-api as well)
  "org.scalikejdbc"               %% "scalikejdbc"                      % "3.4.2",
  "org.scalikejdbc"               %% "scalikejdbc-syntax-support-macro" % "3.4.2",
  "org.scalikejdbc"               %% "scalikejdbc-test"                 % "3.4.2" % "test",
  "org.xerial"                    %  "sqlite-jdbc"                      % "3.32.3",

  // Import/Export
  "org.apache.commons"            % "commons-compress"                  % "1.20",

  // Testing
  "org.specs2"                    %%  "specs2-core"                     % "4.8.2" % "test",
  "org.specs2"                    %%  "specs2-matcher-extra"            % "4.8.2" % "test",
  "org.specs2"                    %%  "specs2-junit"                    % "4.8.2" % "test",

  // jetty
  "javax.servlet"                 %   "javax.servlet-api"               % "3.1.0",

  // command-specific libraries
  // "org.clapper"                   %% "markwrap"                         % "1.2.0"
  "com.github.andyglow"           %% "scala-jsonschema"                 % "0.7.1",
  "com.github.andyglow"           %% "scala-jsonschema-play-json"       % "0.7.1",
)

////// Publishing Metadata //////
// use `sbt publish make-pom` to generate 
// a publishable jar artifact and its POM metadata

pomExtra := <url>http://vizierdb.info</url>
  <licenses>
    <license>
      <name>Apache License 2.0</name>
      <url>http://www.apache.org/licenses/</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:vizierdb/vizier-scala.git</url>
    <connection>scm:git:git@github.com:vizierdb/vizier-scala.git</connection>
  </scm>

// The upstream components pose a slight annoyance here.  If they're checked
// out already, then the POM file gets generated properly, using the appropriate
// build.sbt settings from those projects.  However, if not, then we end up
// defaulting to settings from *this* file.  OTOH, there doesn't seem to be a 
// way to incorporate upstream classpaths without marking them as an explicit
// dependency.  ANNOYING!  To avoid having to check-out upstream packages on 
// the build server, we just give the "default" package settings a dummy groupId
// and strip them out of the automatically derived dependencies here.
import scala.xml.{ Node => XNode }
pomPostProcess := { (node:XNode) => 
  import scala.xml._
  import scala.xml.transform._
  val stripLocalDependencies = new RewriteRule { 
    override def transform(n: Node): Seq[Node] = 
    {
      if(n.label.equals("dependency")){
        if( (n \ "groupId").text.equals("remove-me") ){
          Seq() // ~= remove this dependency
        } else { n }
      } else { n }
    }
  }
  (new RuleTransformer(stripLocalDependencies)).transform(node).head
}

/////// Publishing Options ////////
// use `sbt publish` to update the package in 
// your own local ivy cache

publishMavenStyle := true
publishTo := Some(MavenCache("local-maven",  file("/var/www/maven_repo/")))

// publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

///////////////////////////////////////////
/////// Build and update the UI
///////////////////////////////////////////
lazy val buildUI = taskKey[Unit]("Build the UI")
buildUI := {
  import java.lang.ProcessBuilder
  import java.nio.file.{ Files, Paths }
  import collection.JavaConverters._

  val sourceDir = Paths.get("upstream/ui")
  val source = sourceDir.resolve("build")
  val target = Paths.get("src/main/resources/ui")

  new ProcessBuilder("yarn", "build")
        .directory(sourceDir.toFile)
        .start
        .waitFor
  
  if(Files.exists(target)){
    new ProcessBuilder("rm", "-r", target.toString)
          .start
          .waitFor
  }
  Files.move(source, target)

  val env = 
    Files.readAllLines(Paths.get("upstream/ui/public/env.js"))
         .asScala
         .mkString("\n")
         .replaceAll("http://localhost:5000", "")

  Files.write(Paths.get("src/main/resources/ui/env.js"), env.getBytes)
}

///////////////////////////////////////////
/////// Build a release
///////////////////////////////////////////
lazy val bootstrap = taskKey[Unit]("Generate Bootstrap Jar")
bootstrap := {
  import scala.sys.process._
  import java.nio.file.{ Files, Paths }

  val logger = ProcessLogger(println(_), println(_))
  val coursier_bin = "upstream/coursier"
  val coursier_url = "https://git.io/coursier-cli"
  val vizier_bin = "bin/vizier"
  if(!Files.exists(Paths.get(coursier_bin))){

    println("Downloading Coursier...")
    Process(List(
      "curl", "-L",
      "-o", coursier_bin,
      coursier_url
    )) ! logger match {
      case 0 => 
      case n => sys.error(s"Could not download Coursier")
    }
    Process(List(
      "chmod", "+x", coursier_bin
    )) ! logger
    println("... done")
  }

  println("Coursier available.  Generating Repository List")

  val resolverArgs = resolvers.value.map { 
    case r: MavenRepository => 
      if(!r.root.startsWith("file:")){
        Seq("-r", r.root)
      } else { Seq() }
  }

  val (art, file) = packagedArtifact.in(Compile, packageBin).value
  val qualified_artifact_name = file.name.replace(".jar", "").replaceFirst("-([0-9.]+(-SNAPSHOT)?)$", "")
  val full_artifact_name = s"${organization.value}:${qualified_artifact_name}:${version.value}"
  println("Rendering bootstraps for "+full_artifact_name)
  for(resolver <- resolverArgs){
    println("  "+resolver)
  }

  println("Generating Vizier binary")

  val cmd = List(
    coursier_bin,
    "bootstrap",
    full_artifact_name,
    "-f",
    "-o", vizier_bin,
    "-r", "central"
  )++resolverArgs.flatten
  println(cmd.mkString(" "))

  Process(cmd) ! logger match {
      case 0 => 
      case n => sys.error(s"Bootstrap failed")
  }
}

lazy val updateBootstrap = taskKey[Unit]("Update Local Bootstrap Jars")
updateBootstrap := {
  import java.nio.file.{ Files, Paths }
  import scala.sys.process._
  val (art, file) = packagedArtifact.in(Compile, packageBin).value
  val home = Paths.get(System.getProperty("user.home"))
  val coursier_cache = home.resolve(".cache").resolve("coursier")
  if(Files.exists(coursier_cache)){
    val maven_mimir = 
      coursier_cache.resolve("v1")
                    .resolve("https")
                    .resolve("maven.mimirdb.info")
    val qualified_artifact_name = file.name.replace(".jar", "").replaceFirst("-([0-9.]+(-SNAPSHOT)?)$", "")
    val pathComponents = 
      (organization.value.split("\\.") :+ qualified_artifact_name :+ version.value)
    val repo_dir = pathComponents.foldLeft(maven_mimir) { _.resolve(_) }
    val target = repo_dir.resolve(s"$qualified_artifact_name-${version.value}.jar")
    val targetPom = repo_dir.resolve(s"$qualified_artifact_name-${version.value}.pom")

    
    if(Files.exists(target)){
      {
        val cmd = Seq("cp", file.toString, target.toString)
        println(cmd.mkString(" "))
        Process(cmd) .!!
      }

      {
        val cmd = Seq("cp", file.toString.replace(".jar", ".pom"), targetPom.toString)
        println(cmd.mkString(" "))
        Process(cmd) .!!
      }

    } else {
      println(s"$target does not exist")
    }


  } else { 
    println(s"$coursier_cache does not exist")
  }
}

///////////////////////////////////////////
/////// Helpful command to get dependencies
///////////////////////////////////////////
lazy val checkout = taskKey[Unit]("Check Out Dependencies")
checkout := {
  import java.lang.ProcessBuilder
  import java.nio.file.{ Files, Paths }

  val upstream = Paths.get("upstream")
  if(!Files.exists(upstream)){
    Files.createDirectory(upstream)
  }
  Seq(
    ("Vizier UI", "git@github.com:VizierDB/web-ui.git"      , "ui"     , Some("scala")), 
    ("Mimir"    , "git@github.com:UBOdin/mimir-api.git"     , "mimir"  , None),
    ("Caveats"  , "git@github.com:UBOdin/mimir-caveatgs.git", "caveats", None),
  ).foreach { case (name, repo, stub, branch) => 
    val dir = upstream.resolve(stub)
    if(!Files.exists(dir.resolve(".git"))){
      println(s"Checking out $name into $dir")
      if(!Files.exists(dir)){
        val cmd = Seq("git", "clone", repo) ++ branch.toSeq.flatMap { Seq("-b", _) } ++ Seq(dir.toString)
        new ProcessBuilder(cmd:_*)
              .start
              .waitFor
      } else { 
        // need a little bit of a hack since SBT creates project directories with 
        // a target folder automatically -- a straight checkout fails due to 
        // non-empty repo
        new ProcessBuilder("git", "init", dir.toString)
              .start
              .waitFor
        new ProcessBuilder("git", "remote", "add", "origin", repo)
              .directory(dir.toFile)
              .start
              .waitFor
        new ProcessBuilder("git", "pull", "origin", branch.getOrElse("master"))
              .directory(dir.toFile)
              .start
              .waitFor
      }
      
    } else { 
      println(s"$name already checked out")
    }
  }
}

///////////////////////////////////////////
/////// Attach copyright information to all files
///////////////////////////////////////////
lazy val fixCopyrights = taskKey[Unit]("Update copyright headers on files")
fixCopyrights := {
  import java.nio.file.{ Files, Path }
  import scala.io.Source
  import sbt.nio.file.FileTreeView
  import sbt.io.RegularFileFilter
  import java.io.{ BufferedWriter, FileWriter, OutputStreamWriter }
  val licenseLines = Source.fromFile("LICENSE.txt").getLines.toSeq
  val firstLine = "-- copyright-header:v1 --"
  val lastLine  = "-- copyright-header:end --"
  def license(start: String = "", end: String = "", line: String = "") = 
    (start+firstLine) +: (licenseLines :+ (lastLine+end)).map { line+_ }

  // val scalaFiles = Glob("src") / **
  // println(FileTreeView.default.list(scalaFiles).toSeq)

  def injectHeaderIfNeeded(file: Path, start: String, end: String, line: String) =
  {
    val lines = Source.fromFile(file.toString).getLines.toIndexedSeq
    if(!lines.head.equals(start+firstLine)){
      println(s"Fixing $file")
      val tempFile = file.resolveSibling(file.getFileName()+".tmp")
      val writer = Files.newBufferedWriter(tempFile)
      // val writer = new OutputStreamWriter(System.out)
      val linesPlusBlankEnd = 
      if(!lines.reverse.head.equals("")) { lines :+ "" } 
        else {lines}

      for(line <- (license(start, end, line) ++ linesPlusBlankEnd)){
        // println(s"Writing: $line")
        writer.write((line+"\n"))
      }
      writer.flush()
      writer.close()
      Files.delete(file)
      Files.move(tempFile, file)
    } else { 
      // println(s"No need to fix $file")
    }
  }

  fileTreeView.value.list(
    Glob(s"${baseDirectory.value}/src/**"), 
    RegularFileFilter.toNio && "**/*.scala"
  ).map { _._1 }
   // .take(1)
   .foreach { injectHeaderIfNeeded(_, "/* ", " */", " * ") }

  fileTreeView.value.list(
    Glob(s"${baseDirectory.value}/src/**"), 
    RegularFileFilter.toNio && "**/*.py"
  ).map { _._1 }
   // .take(2)
   .foreach { injectHeaderIfNeeded(_, "# ", "", "# ") }

}

///////////////////////////////////////////
/////// Render the routes
///////////////////////////////////////////
lazy val routes = taskKey[Unit]("Render routes table")
routes := {
  import scala.io.Source
  import java.nio.file.{ Files, Paths }
  
  val PARAMETER = "\\{([^}:]+):([^}]+)\\}".r
  
  val parsed: Seq[(String, Option[String], String, String, String)] = 
    Source.fromFile("src/main/resources/vizier-routes.txt")
          .getLines
          .map { _.split(" +") }
          .zipWithIndex
          .map { x => 
            val idx = x._2
            val path = x._1(0)
            val pathComponents = path.split("/")
            val httpVerb = x._1(1)
            val handler = x._1(2)

            val (pathPattern, fieldsPerComponent) =
              pathComponents.map {
                case PARAMETER(param, "int") => 
                  ("([0-9]+)", Some((param, s"JsNumber(${param}.toLong)")))
                case PARAMETER(param, "subpath") =>
                  ("(.+)", Some((param, s"JsString(${param})")))
                case p if p.startsWith("{") || p.contains("\"") => 
                  throw new IllegalArgumentException(s"Invalid path parameter ${p} in ${x}")
                case p => (p, None)
              }.unzip

            val fields = fieldsPerComponent.flatten
            val (matcher, patternDef) = 
              if(fields.isEmpty){
                ("\""+pathPattern.mkString("/")+"\"", None)
              } else {
                (
                  "ROUTE_PATTERN_"+idx+"("+fields.map { _._1 }.mkString(", ")+")",
                  Some("val ROUTE_PATTERN_"+idx+" = \""+pathPattern.mkString("/")+"\".r")
                )
              }

            val fieldConstructor = { 
                "Map("+
                  fields.map { case (name, parser) => "\""+name+"\" -> "+parser }
                        .mkString(", ")+
                ")"
              }

            val handlerParameters = 
              Seq(fieldConstructor, "request")

            val invokeHandler = 
              s"case $matcher => ${handler}.handle(${handlerParameters.mkString(", ")})"
            
            ( 
              httpVerb,
              patternDef,
              invokeHandler,
              matcher,
              path
            )
          }
          .toSeq

  val patternDefs = parsed.flatMap { _._2 }.map { "  "+_ }.mkString("\n")

  val handlers = 
    parsed.groupBy { _._1 }
          .map { case (verb, handlers) => 
            val capsedVerb = verb.substring(0,1).toUpperCase()+verb.substring(1).toLowerCase()
            (Seq(
              s" override def do${capsedVerb}(request: HttpServletRequest, output: HttpServletResponse) = ",
              "  {",
              "    processResponse(request, output) {",
              "      request.getPathInfo match {" ,
            )++ handlers.map { "        "+_._3 }++Seq(
              "        case _ => fourOhFour(request)",
              "      }",
              "    }",
              "  }"
            )).mkString("\n")
          }
          .mkString("\n\n")

  val corsHandler:String = (
      Seq(
        "  override def doOptions(request: HttpServletRequest, output: HttpServletResponse) = ",
        "  {",
        "    processResponse(request, output) {",
        "      request.getPathInfo match {" ,
      )++(
        parsed.groupBy { _._5 }
              .map { case (_, baseHandlers) => 
                s"        case ${baseHandlers.head._4} => CORSPreflightResponse("+baseHandlers.map { "\""+_._1+"\"" }.mkString(", ")+")"
              }
      )++Seq(
        "        case _ => fourOhFour(request)",
        "      }",
        "    }",
        "  }",
      )
    ).mkString("\n")

  val routeFile = Seq(
    "package info.vizierdb.api.servlet",
    "",
    "/* this file is AUTOGENERATED by `sbt routes` from `src/main/resources/vizier-routes.txt` */",
    "/* DO NOT EDIT THIS FILE DIRECTLY */",
    "",
    "import play.api.libs.json._",
    "import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}",
    "import org.mimirdb.api.Response",
    "import info.vizierdb.types._",
    "import info.vizierdb.api._",
    "import info.vizierdb.api.response.CORSPreflightResponse",
    "import info.vizierdb.api.handler._",
    "",
    "trait VizierAPIServletRoutes extends HttpServlet {",
    "",
    "  def processResponse(request: HttpServletRequest, output: HttpServletResponse)(response: => Response): Unit",
    "  def fourOhFour(request: HttpServletRequest): Response",
    "",
    patternDefs,
    "",
    handlers,
    "",
    corsHandler,
    "}"
  ).mkString("\n")

  Files.write(
    Paths.get("src/main/scala/info/vizierdb/api/servlet/VizierAPIServletRoutes.scala"), 
    routeFile.getBytes()
  )
}