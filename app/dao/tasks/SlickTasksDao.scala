package dao.tasks

import javax.inject.Inject

import dao.SlickDriver
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future


class SlickTasksDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends TasksDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[Tasks] = Tasks

  override def getTaskWithoutTimeSlots(offset: Int, limit: Int): Future[(Seq[TasksRow])] = {
    val tasksWithoutTimeSlotsQuery = query.filter(_.timeSlotId.isEmpty)
      .drop(offset).take(limit)
      .sortBy(_.scheduledTime.asc)
    run(tasksWithoutTimeSlotsQuery.result)
  }
}
