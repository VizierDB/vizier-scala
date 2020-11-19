name := "vizier" 
version := "0.1-SNAPSHOT"
organization := "info.vizierdb"
scalaVersion := "2.12.10"

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
  ExclusionRule( organization = "org.xerial"), 
  ExclusionRule( organization = "org.mortbay.jetty"), 
  ExclusionRule( organization = "javax.servlet") ,
)

// Custom Dependencies
libraryDependencies ++= Seq(
  // Mimir
  "org.mimirdb"                   %% "mimir-api"                        % "0.2-SNAPSHOT",
  "org.mimirdb"                   %% "mimir-caveats"                    % "0.2.8",

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

/////// Publishing Options ////////
// use `sbt publish` to update the package in 
// your own local ivy cache

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
