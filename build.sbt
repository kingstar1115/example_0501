import com.typesafe.config.ConfigFactory

name := """qweex-backend"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  "google-sedis-fix" at "http://pk11-scratch.googlecode.com/svn/trunk"
)

libraryDependencies ++= Seq(cache, ws, filters, "postgresql" % "postgresql" % "9.1-901.jdbc4",
  "com.typesafe.play" %% "play-slick" % "1.1.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.1.0",
  "com.typesafe.slick" %% "slick-codegen" % "3.1.0",
  "com.typesafe.play.modules" %% "play-modules-redis" % "2.4.0",
  "com.github.t3hnar" % "scala-bcrypt_2.10" % "2.5",
  "com.typesafe.play" %% "play-mailer" % "3.0.1",
  "com.stripe" % "stripe-java" % "1.45.0",
  "com.relayrides" % "pushy" % "0.8.1",
  "io.netty" % "netty-tcnative-boringssl-static" % "1.1.33.Fork22"
)

routesGenerator := InjectedRoutesGenerator

val conf = ConfigFactory.parseFile(new File("conf/application.conf")).resolve()
slick <<= slickCodeGenTask

// code generation task
lazy val slick = TaskKey[Seq[File]]("gen-tables")
lazy val slickCodeGenTask = (sourceManaged, fullClasspath in Compile, runner in Compile, streams) map { (dir, cp, r, s) =>
  val outputDir = root.base.getAbsoluteFile / "app"
  val url = conf.getString("slick.dbs.default.db.url")
  val jdbcDriver = conf.getString("slick.dbs.default.db.driver")
  val slickDriver = conf.getString("slick.dbs.default.driver").dropRight(1)
  val pkg = "models"
  val user = conf.getString("slick.dbs.default.db.user")
  val password = conf.getString("slick.dbs.default.db.password")
  toError(r.run("commons.utils.codegen.ExtSourceCodeGenerator",
    cp.files,
    Array(slickDriver, jdbcDriver, url, outputDir.getPath, pkg, user, password), s.log))
  val fname = outputDir + s"/$pkg/Tables.scala"
  Seq(file(fname))
}
