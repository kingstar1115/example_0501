package services.internal.dayslots

import java.sql.{Date => SQLDate}
import javax.inject.Inject

import dao.dayslots.{BookingDao, TimeSlotQueryObject}
import dao.tasks.{TaskQueryObject, TasksDao}
import dao.{SlickDbService, SlickDriver}
import models.Tables
import models.Tables._
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.bookings.BookingService
import services.internal.settings.SettingsService

import scala.concurrent.Future

class DefaultDaySlotService @Inject()(bookingDao: BookingDao,
                                      tasksDao: TasksDao,
                                      settingsService: SettingsService,
                                      slickDbService: SlickDbService,
                                      bookingService: BookingService) extends DaySlotsService with SlickDriver {

  private val timeSlotQueryObject = TimeSlotQueryObject
  private val taskQueryObject = TaskQueryObject

  override def findByDate(date: SQLDate): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    bookingDao.findByDateWithTimeSlots(date)
  }

  override def findByDates(dates: Set[SQLDate]): Future[Seq[Tables.DaySlotsRow]] = {
    bookingDao.findByDates(dates)
  }

  override def createDaySlotWithTimeSlots(date: SQLDate): Future[(DaySlotsRow, Seq[TimeSlotsRow])] = {
    bookingService.createDaySlotWithTimeSlots(date)
  }

  override def bookTimeSlot(task: TasksRow, timeSlot: TimeSlotsRow, validateCapacity: Boolean = true): Future[Int] = {
    val newReservedAmount = timeSlot.reserved + 1
    if (validateCapacity) {
      if (newReservedAmount > timeSlot.capacity) {
        throw new IllegalArgumentException("Time slot bookings reached maximum capacity")
      }
    }
    val updateAction = for {
      _ <- timeSlotQueryObject.updateQuery(timeSlot.copy(reserved = newReservedAmount))
      tasksUpdateCount <- taskQueryObject.updateQuery(task.copy(timeSlotId = timeSlot.id))
    } yield tasksUpdateCount
    slickDbService.run(updateAction)
  }
}

