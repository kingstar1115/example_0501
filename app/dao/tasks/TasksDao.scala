package dao.tasks

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables.{Tasks, TasksRow}

import scala.concurrent.Future

@ImplementedBy(classOf[SlickTasksDao])
trait TasksDao extends EntityDao[Tasks, TasksRow] {

  def getTaskWithoutTimeSlots(offset: Int, limit: Int): Future[(Seq[TasksRow])]

}
