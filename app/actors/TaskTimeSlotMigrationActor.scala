package actors

import java.sql.Date
import java.time.LocalTime
import javax.inject.Inject

import actors.TaskTimeSlotMigrationActor._
import akka.actor.Actor
import dao.dayslots.BookingDao
import dao.tasks.TasksDao
import models.Tables
import models.Tables._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.dayslots.DaySlotsService

import scala.concurrent.Future


class TaskTimeSlotMigrationActor @Inject()(tasksDao: TasksDao,
                                           bookingDao: BookingDao,
                                           daySlotsService: DaySlotsService) extends Actor {

  override def receive: Receive = {
    case x: MigrateTasks =>
      Logger.info("Starting tasks migration to time slots")
      migrate(x.offset, x.limit, 0)
        .map(count => Logger.info(s"Migrated $count tasks"))
  }

  private def migrate(offset: Int, limit: Int, count: Int): Future[Int] = {

    def accumulateTasks(accumulator: Future[Int], tasks: Seq[TasksRow]): Future[Int] = {
      if (tasks.isEmpty)
        accumulator
      else {
        accumulateTasks(accumulator.flatMap(count => mapTaskWithSlot(tasks.head, count)), tasks.tail)
      }
    }

    tasksDao.getTaskWithoutTimeSlots(offset, limit).flatMap {
      case tasks if tasks.isEmpty =>
        Future.successful(count)
      case tasks =>
        accumulateTasks(mapTaskWithSlot(tasks.head, 0), tasks.tail).flatMap {
          updatedRowsCount =>
            val totalUpdatedTasksCount = count + updatedRowsCount
            if (updatedRowsCount < limit)
              Future.successful(totalUpdatedTasksCount)
            else
              migrate(offset, limit, totalUpdatedTasksCount)
        }
    }
  }

  private def mapTaskWithSlot(task: TasksRow, count: Int): Future[Int] = {
    findTimeSlotForTask(task).flatMap {
      case Some(timeSlot) =>
        daySlotsService.bookTimeSlot(task, timeSlot, skipCapacityValidation = false)
      case None =>
        createTimeSlotsAndBookSlot(task)
    }.map(_ + count)
  }

  private def findTimeSlotForTask(task: TasksRow): Future[Option[TimeSlotsRow]] = {
    daySlotsService.findByDate(new Date(task.scheduledTime.getTime)).map(_.map {
      case (_, timeSlots) =>
        filterTimeSlotByStartTime(task, timeSlots)
    })
  }

  private def filterTimeSlotByStartTime(task: TasksRow, timeSlots: Seq[Tables.TimeSlotsRow]): TimeSlotsRow = {
    val time = task.scheduledTime.toLocalDateTime.toLocalTime match {
      case localTime if localTime.isBefore(StartTimeBound) =>
        Logger.warn(s"Changing start time from $localTime to $StartTimeBound for task ${task.id}")
        localTime.withHour(StartTimeBound.getHour)
      case localTime if localTime.isAfter(EndTimeBound) =>
        Logger.warn(s"Changing start time from $localTime to $EndTimeBound for task ${task.id}")
        localTime.withHour(EndTimeBound.getHour)
      case localTime =>
        localTime
    }

    timeSlots.filter { timeSlot =>
      val startTime = timeSlot.startTime.toLocalTime
      val endTime = timeSlot.endTime.toLocalTime
      (time == startTime || time.isAfter(startTime)) && (time == endTime || time.isBefore(endTime))
    }.head
  }

  private def createTimeSlotsAndBookSlot(task: TasksRow): Future[Int] = {
    val taskDate = new Date(task.scheduledTime.getTime)
    daySlotsService.createDaySlotWithTimeSlots(taskDate).flatMap {
      case (_, timeSlots) =>
        val timeSlot = filterTimeSlotByStartTime(task, timeSlots)
        daySlotsService.bookTimeSlot(task, timeSlot, skipCapacityValidation = false)
    }
  }

}

object TaskTimeSlotMigrationActor {

  private val StartTimeBound = LocalTime.of(9, 0)
  private val EndTimeBound = LocalTime.of(17, 0)

  case class MigrateTasks(offset: Int, limit: Int)

}
