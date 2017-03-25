package migrations

import java.sql.{Date, Timestamp}
import javax.inject.Inject

import dao.dayslots.DaySlotsDao
import dao.tasks.TasksDao
import models.Tables
import models.Tables.{TasksRow, TimeSlotsRow}
import play.api.Logger
import services.internal.dayslots.DaySlotsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TaskTimeSlotMigration @Inject()(tasksDao: TasksDao,
                                      daySlotsDao: DaySlotsDao,
                                      daySlotsService: DaySlotsService) {

  migrate(0, 100)

  def migrate(offset: Int, limit: Int): Future[Int] = {
    Logger.info(s"Migrating task time slots. Offset: $offset, Limit: $limit")
    tasksDao.getTaskWithoutTimeSlots(offset, limit).flatMap {
      case (tasks, totalCount) =>
        tasks.foreach(mapTaskWithSlot)
        if (offset + limit >= totalCount)
          Future.successful(totalCount)
        else
          migrate(offset + limit, limit)
    }
  }

  private def mapTaskWithSlot(task: TasksRow) = {
    findTimeSlotForTask(task.scheduledTime).map {
      case Some(timeSlot) =>
        daySlotsService.bookTimeSlot(task, timeSlot)
      case None =>
        createTimeSlotsAndBookSlot(task)
    }
  }

  private def findTimeSlotForTask(timestamp: Timestamp): Future[Option[TimeSlotsRow]] = {
    daySlotsService.findByDate(new Date(timestamp.getTime)).map(_.map {
      case (_, timeSlots) =>
        filterTimeSlotByStartTime(timestamp, timeSlots)
    })
  }

  private def filterTimeSlotByStartTime(timestamp: Timestamp, timeSlots: Seq[Tables.TimeSlotsRow]) = {
    val time = timestamp.toLocalDateTime.toLocalTime
    timeSlots.filter(_.startTime.toLocalTime == time).head
  }

  private def createTimeSlotsAndBookSlot(task: TasksRow) = {
    val taskDate = new Date(task.scheduledTime.getTime)
    daySlotsService.createDaySlotWithTimeSlots(taskDate).map {
      case (_, timeSlots) =>
        val timeSlot = filterTimeSlotByStartTime(task.scheduledTime, timeSlots)
        daySlotsService.bookTimeSlot(task, timeSlot)
    }
  }
}

