package services.internal.dayslots

import java.sql.{Date => SQLDate}
import javax.inject.Inject

import commons.utils.TimeUtils
import dao.SlickDriver
import dao.dayslots.{DaySlotQueryObject, BookingDao, TimeSlotQueryObject}
import dao.tasks.{TaskQueryObject, TasksDao}
import models.Tables
import models.Tables._
import services.internal.dayslots.DefaultDaySlotService._
import services.internal.settings.SettingsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultDaySlotService @Inject()(bookingDao: BookingDao,
                                      tasksDao: TasksDao,
                                      settingsService: SettingsService) extends DaySlotsService with TimeUtils with SlickDriver {

  private val daySlotQueryObject = new DaySlotQueryObject
  private val timeSlotQueryObject = new TimeSlotQueryObject
  private val taskQueryObject = new TaskQueryObject

  override def findByDate(date: SQLDate): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    bookingDao.findByDateWithTimeSlots(date)
  }

  override def findByDates(dates: Set[SQLDate]): Future[Seq[Tables.DaySlotsRow]] = {
    bookingDao.findByDates(dates)
  }

  override def createDaySlotWithTimeSlots(date: SQLDate): Future[(DaySlotsRow, Seq[TimeSlotsRow])] = {
    (for {
      dayCapacity <- settingsService.getIntValue(DaySlotCapacity, DefaultDaySlotCapacity)
      timeCapacity <- settingsService.getIntValue(TimeSlotCapacity, DefaultTimeSlotCapacity)
    } yield (dayCapacity, timeCapacity)).flatMap {
      case (dayCapacity, timeCapacity) =>
        val insertAction = for {
          daySlot <- daySlotQueryObject.insertQuery += DaySlotsRow(0, currentTimestamp, date)
          timeSlots <- timeSlotQueryObject.insertQuery ++= createTimeSlots(dayCapacity, timeCapacity, daySlot)
        } yield (daySlot, timeSlots)
        bookingDao.run(insertAction)
    }
  }

  private def createTimeSlots(dayCapacity: Int, timeCapacity: Int, daySlot: DaySlotsRow): Seq[TimeSlotsRow] = {
    val bookingSlotTimestamp = daySlot.date.toSqlTimestamp
    0.until(dayCapacity).map { index =>
      val startHour = TimeSlotsStartHour + index
      TimeSlotsRow(0, currentTimestamp, timeCapacity, 0, bookingSlotTimestamp.resetToHour(startHour).toSqlTime,
        bookingSlotTimestamp.resetToHour(startHour + 1).toSqlTime, daySlot.id)
    }
  }

  override def bookTimeSlot(task: TasksRow, timeSlot: TimeSlotsRow, validateCapacity: Boolean = true): Future[Int] = {
    val bookingsCount = timeSlot.bookingsCount + 1
    if (validateCapacity) {
      if (bookingsCount > timeSlot.capacity) {
        throw new IllegalArgumentException("Time slot bookings reached maximum capacity")
      }
    }
    val updateAction = for {
      _ <- timeSlotQueryObject.updateQuery(timeSlot.copy(bookingsCount = bookingsCount))
      tasksUpdateCount <- taskQueryObject.updateQuery(task.copy(timeSlotId = Some(timeSlot.id)))
    } yield tasksUpdateCount
    bookingDao.run(updateAction)
  }
}

object DefaultDaySlotService {
  val DaySlotCapacity = "day.slot.capacity"
  val DefaultDaySlotCapacity = 8

  val TimeSlotCapacity = "time.slot.capacity"
  val DefaultTimeSlotCapacity = 1

  //Time slots for day starts from 9 am
  val TimeSlotsStartHour = 9
}
