package dao.tasks

import java.sql.Timestamp
import java.time.LocalDateTime

import commons.enums.TaskStatuses
import dao.SlickDriver
import javax.inject.Inject
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future


class SlickTasksDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends TasksDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[Tasks] = Tasks

  override def getOverdueTasks(scheduledDateTime: LocalDateTime): Future[Seq[TasksRow]] = {
    val query = for {
      task <- Tasks
      //Calling `plusHours` because car wash usually takes one hour
      if task.jobStatus.inSet(TaskStatuses.activeStatuses) && task.scheduledTime < Timestamp.valueOf(scheduledDateTime.plusHours(1))
    } yield task
    this.run(query.result)
  }
}
