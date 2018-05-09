package dao.tasks

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables.{Tasks, TasksRow}

import scala.concurrent.Future

@ImplementedBy(classOf[SlickTasksDao])
trait TasksDao extends EntityDao[Tasks, TasksRow] {

  def getOverdueTasks(scheduledDateTime: LocalDateTime): Future[Seq[TasksRow]]

}
