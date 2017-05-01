package commons.utils.codegen

import play.api.libs.concurrent.Execution.Implicits._
import slick.codegen.SourceCodeGenerator
import slick.driver.JdbcProfile
import slick.{model => m}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class ExtSourceCodeGenerator(model: m.Model) extends SourceCodeGenerator(model: m.Model) {

  def relationTables: Seq[String] = Seq("services_extras")

  override def Table = new Table(_) {

    override def TableClass = new TableClass {
      override def code: String =
        if (relationTables.contains(model.name.table)) {
          super.code
        } else {
          val args = model.name.schema.map(n => s"""Some("$n")""") ++ Seq("\"" + model.name.table + "\"")
          s"""
            class $name(_tableTag: Tag) extends BaseTable[$elementType](_tableTag, ${args.mkString(", ")}) {
              ${indent(body.map(_.mkString("\n")).mkString("\n\n"))}
            }
          """.trim()
        }
    }

    override def EntityType = new EntityType {
      override def parents: Seq[String] = {
        if (relationTables.contains(model.name.table))
          Seq()
        else
          Seq("Entity")
      }
    }
  }
}

object ExtSourceCodeGenerator {

  def run(slickDriver: String, jdbcDriver: String, url: String, outputDir: String, pkg: String, user: Option[String], password: Option[String]): Unit = {
    val driver: JdbcProfile =
      Class.forName(slickDriver + "$").getField("MODULE$").get(null).asInstanceOf[JdbcProfile]
    val dbFactory = driver.api.Database
    val db = dbFactory.forURL(url, driver = jdbcDriver,
      user = user.orNull, password = password.orNull, keepAliveConnection = true)
    try {
      val filteredTables = driver.defaultTables.map(_.filter(table => !table.name.name.equals("play_evolutions")))
      val m = Await.result(
        db.run(driver.createModel(Some(filteredTables), false)(ExecutionContext.global).withPinnedSession),
        Duration.Inf
      )
      new ExtSourceCodeGenerator(m).writeToFile(slickDriver, outputDir, pkg)
    } finally db.close
  }

  def main(args: Array[String]): Unit = {
    args.toList match {
      case slickDriver :: jdbcDriver :: url :: outputDir :: pkg :: Nil =>
        run(slickDriver, jdbcDriver, url, outputDir, pkg, None, None)
      case slickDriver :: jdbcDriver :: url :: outputDir :: pkg :: user :: password :: Nil =>
        run(slickDriver, jdbcDriver, url, outputDir, pkg, Some(user), Some(password))
      case _ =>
        println(
          """
            |Usage:
            |  SourceCodeGenerator configURI [outputDir]
            |  SourceCodeGenerator slickDriver jdbcDriver url outputDir pkg [user password]
            |
            |Options:
            |  configURI: A URL pointing to a standard database config file (a fragment is
            |    resolved as a path in the config), or just a fragment used as a path in
            |    application.conf on the class path
            |  slickDriver: Fully qualified name of Slick driver class, e.g. "slick.driver.H2Driver"
            |  jdbcDriver: Fully qualified name of jdbc driver class, e.g. "org.h2.Driver"
            |  url: JDBC URL, e.g. "jdbc:postgresql://localhost/test"
            |  outputDir: Place where the package folder structure should be put
            |  pkg: Scala package the generated code should be places in
            |  user: database connection user name
            |  password: database connection password
            |
            |When using a config file, in addition to the standard config parameters from
            |slick.backend.DatabaseConfig you can set "codegen.package" and
            |"codegen.outputDir". The latter can be overridden on the command line.
          """.stripMargin.trim)
        System.exit(1)
    }
  }
}

