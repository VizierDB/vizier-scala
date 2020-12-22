scalaVersion := "2.12.10"

val MIMIR_VERSION = "0.2"
val CAVEATS_VERSION = "0.2.9"

// Project and subprojects
lazy val vizier = (project in file("."))
                      .dependsOn(
                        mimir, 
                        caveats
                      )
                      .settings(
                        name := "vizier",
                        version := "0.1-SNAPSHOT",
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
  ExclusionRule( organization = "javax.servlet") ,
)

// Custom Dependencies
libraryDependencies ++= Seq(
  // Mimir
  "org.mimirdb"                   %% "mimir-api"                        % MIMIR_VERSION,
  "org.mimirdb"                   %% "mimir-caveats"                    % CAVEATS_VERSION,

  // Catalog management (trying this out... might be good to bring into mimir-api as well)
  // "org.squeryl"                   %% "squeryl"                   % "0.9.15",
  "org.scalikejdbc"               %% "scalikejdbc"                      % "3.4.2",
  "org.scalikejdbc"               %% "scalikejdbc-syntax-support-macro" % "3.4.2",
  "org.scalikejdbc"               %% "scalikejdbc-test"                 % "3.4.2" % "test",
  "org.xerial"                    %  "sqlite-jdbc"                      % "3.32.3",

  // Testing
  "org.specs2"                    %%  "specs2-core"                     % "4.8.2" % "test",
  "org.specs2"                    %%  "specs2-matcher-extra"            % "4.8.2" % "test",
  "org.specs2"                    %%  "specs2-junit"                    % "4.8.2" % "test",

  // jetty
  "javax.servlet"                 %   "javax.servlet-api"               % "3.1.0",

  // command-specific libraries
  // "org.clapper"                   %% "markwrap"                         % "1.2.0"
)

////// Publishing Metadata //////
// use `sbt publish make-pom` to generate 
// a publishable jar artifact and its POM metadata

publishMavenStyle := true

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

publishTo := Some(Resolver.file("file",  new File("/var/www/maven_repo/")))
// publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

///////////////////////////////////////////
/////// Build and update the UI
///////////////////////////////////////////
lazy val buildUI = taskKey[Unit]("Build the UI")
buildUI := {
  import java.lang.ProcessBuilder
  import java.nio.file.{ Files, Paths }

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
  }.flatten

  val (art, file) = packagedArtifact.in(Compile, packageBin).value
  val qualified_artifact_name = file.name.replace(".jar", "").replaceFirst("-([0-9.]+(-SNAPSHOT)?)$", "")
  val full_artifact_name = s"${organization.value}:${qualified_artifact_name}:${version.value}"
  println("Rendering bootstraps for "+full_artifact_name)
  for(resolver <- resolverArgs){
    println("  "+resolver)
  }
  println
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
    ("Vizier UI", "git@github.com:VizierDB/web-ui.git"      , "ui"     ), 
    ("Mimir"    , "git@github.com:UBOdin/mimir-api.git"     , "mimir"  ),
    ("Caveats"  , "git@github.com:UBOdin/mimir-caveatgs.git", "caveats"),
  ).foreach { case (name, repo, stub) => 
    val dir = upstream.resolve(stub)
    if(!Files.exists(dir.resolve(".git"))){
      println(s"Checking out $name into $dir")
      if(!Files.exists(dir)){
        new ProcessBuilder("git", "clone", repo, dir.toString)
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
        new ProcessBuilder("git", "pull", "origin", "master")
              .directory(dir.toFile)
              .start
              .waitFor
      }
      
    } else { 
      println(s"$name already checked out")
    }
  }

}
