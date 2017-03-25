package dao.tasks

import javax.inject.Inject

import dao.SlickDriver
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SlickTasksDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends TasksDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[Tasks] = Tasks

  override def getTaskWithoutTimeSlots(offset: Int, limit: Int): Future[(Seq[TasksRow], Int)] = {
    val tasksWithoutTimeSlotsQuery = filter(_.timeSlotId.isEmpty)
    val action = for {
      tasks <- tasksWithoutTimeSlotsQuery.drop(offset).take(limit).result
      totalCount <- tasksWithoutTimeSlotsQuery.length.result
    } yield (tasks, totalCount)
    run(action)
  }
}
