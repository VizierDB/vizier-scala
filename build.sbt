scalaVersion := "2.12.12"

val VIZIER_VERSION = "1.1.0-SNAPSHOT"
val MIMIR_VERSION = "1.1.0-SNAPSHOT"
val CAVEATS_VERSION = "0.3.1"

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
resolvers += "Open Source Geospatial Foundation Repository" at "https://repo.osgeo.org/repository/release/"

excludeDependencies ++= Seq(
  // Hadoop brings in more logging backends.  Kill it with fire.
  ExclusionRule("org.slf4j", "slf4j-log4j12"),
  // Jetty shows up in infinite places with incompatible java servlet APIs
  // ExclusionRule( organization = "org.xerial"), 
  ExclusionRule( organization = "org.mortbay.jetty"), 
)

dependencyOverrides ++= Seq(
  "javax.servlet"  % "javax.servlet-api"          % "3.1.0"
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
  "org.eclipse.jetty.websocket"   %   "websocket-server"                % "9.4.10.v20180503",

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

  val relevantResolvers:Seq[String] = resolvers.value.flatMap { 
    case r: MavenRepository => 
      if(!r.root.startsWith("file:")){
        Some(r.root)
      } else { None }
  }
  val resolverArgs:Seq[String] = relevantResolvers.flatMap { Seq("-r", _) }

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
  )++resolverArgs
  println(cmd.mkString(" "))

  Process(cmd) ! logger match {
      case 0 => 
      case n => sys.error(s"Bootstrap failed")
  }

  println("Building Coursier Manifest")

  val manifest = (Seq(
    "{",
    "  \"vizier\": {",
    "    \"mainClass\": \"info.vizierdb.Vizier\",",
    "    \"repositories\": [",
    "      \"central\": ",
  )++relevantResolvers.map { r => 
    ",     \""+r+"\""
  }++Seq(
    "    ],",
    "    \"dependencies\": [",
    "      \""+full_artifact_name+"\"",
    "    ]",
    "  }",
    "}",
  ))

  Files.write(Paths.get("target/coursier.json"), manifest.mkString("\n").getBytes)

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
    ("Vizier UI", "git@github.com:VizierDB/web-ui.git"     , "ui"     , Some("scala")), 
    ("Mimir"    , "git@github.com:UBOdin/mimir-api.git"    , "mimir"  , None),
    ("Caveats"  , "git@github.com:UBOdin/mimir-caveats.git", "caveats", None),
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
/////// Render the routes
///////////////////////////////////////////
lazy val routes = taskKey[Unit]("Render routes table")
routes := {
  Process("python3 scripts/build_routes.py")
}