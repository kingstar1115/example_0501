package dao.tasks

import java.sql.Timestamp
import java.time.LocalDateTime

import commons.enums.TaskStatuses
import javax.inject.Inject
import models.Tables._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

class SlickTasksDao @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends TasksDao with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  override def query: TableQuery[Tasks] = Tasks

  override def getOverdueTasks(scheduledDateTime: LocalDateTime): Future[Seq[TasksRow]] = {
    val query = sql"""SELECT * FROM tasks
         WHERE job_status IN (#${TaskStatuses.activeStatuses.mkString(",")})
         AND (scheduled_time + '2 hours'::interval) < ${Timestamp.valueOf(scheduledDateTime)}""".as[TasksRow]
    this.run(query)
  }
}
