import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.{ MavenRepository }

object mimir {
  val VERSION = "1.1.0-SNAPSHOT"
}

object caveats {
  val VERSION = "0.3.2"
}

object vizier extends ScalaModule with PublishModule {
  val VERSION = "1.2.0-SNAPSHOT"

  def scalaVersion = "2.12.12"

  def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
    MavenRepository("https://maven.mimirdb.info/"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://repo.osgeo.org/repository/release/")
  )}

  def ivyDeps = Agg(
    ////////////////////// Mimir ///////////////////////////
    ivy"org.mimirdb::mimir-api::${mimir.VERSION}"
      .exclude(
        "org.slf4j" -> "slf4j-log4j12",
        "org.mortbay.jetty" -> "*"
      ),
    ivy"org.mimirdb::mimir-caveats::${caveats.VERSION}"
      .exclude(
        "org.slf4j" -> "slf4j-log4j12",
        "org.mortbay.jetty" -> "*"
      ),

    ////////////////////// Catalog Management //////////////
    ivy"org.scalikejdbc::scalikejdbc::3.4.2",
    ivy"org.scalikejdbc::scalikejdbc-syntax-support-macro::3.4.2",
    ivy"org.xerial:sqlite-jdbc:3.32.3",


    ////////////////////// Import/Export Support ///////////
    ivy"org.apache.commons:commons-compress:1.20",

    ////////////////////// API Support /////////////////////
    ivy"javax.servlet:javax.servlet-api:3.1.0",
    ivy"org.eclipse.jetty.websocket:websocket-server:9.4.10.v20180503",

    ////////////////////// Command-Specific Libraries //////
    ivy"com.github.andyglow::scala-jsonschema::0.7.1",
    ivy"com.github.andyglow::scala-jsonschema-play-json::0.7.1",

    ////////////////////// Logging /////////////////////////
    ivy"com.typesafe.scala-logging::scala-logging::3.9.2",
    ivy"ch.qos.logback:logback-classic:1.2.3",
  )

  object test extends Tests with TestModule.Specs2 {

    def scalacOptions = Seq("-Yrangepos")
      def ivyDeps = Agg(
        ivy"org.scalikejdbc::scalikejdbc-test::3.4.2",
        ivy"org.specs2::specs2-core::4.8.2",
        ivy"org.specs2::specs2-matcher-extra::4.8.2",
        ivy"org.specs2::specs2-junit::4.8.2",
      )

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

}